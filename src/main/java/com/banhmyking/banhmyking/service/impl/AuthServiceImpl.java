package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.auth.ChangePasswordRequest;
import com.banhmyking.banhmyking.dto.auth.LoginRequest;
import com.banhmyking.banhmyking.dto.auth.RegisterRequest;
import com.banhmyking.banhmyking.dto.auth.TokenResponse;
import com.banhmyking.banhmyking.entity.RefreshToken;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.RefreshTokenRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import com.banhmyking.banhmyking.service.AuthService;
import com.banhmyking.banhmyking.service.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 32 byte ngẫu nhiên — đủ để token trong link email không đoán được. */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${jwt.refresh-token-expiry-days}")
    private int refreshTokenExpiryDays;

    @Value("${app.verification-token-expiry-hours}")
    private int verificationTokenExpiryHours;

    @Value("${app.reset-token-expiry-minutes}")
    private int resetTokenExpiryMinutes;

    // ─── Register ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email đã tồn tại");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setEmailVerified(false);
        String rawToken = assignVerificationToken(user);
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), rawToken);
    }

    // ─── Verify email ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {
        User user = userRepository.findByVerificationTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Link xác thực không hợp lệ hoặc đã được sử dụng"));

        if (user.getVerificationTokenExpiresAt() == null
                || user.getVerificationTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Link xác thực đã hết hạn. Vui lòng yêu cầu gửi lại email xác thực.");
        }

        user.setEmailVerified(true);
        // Token dùng một lần: xoá để link cũ không xác thực lại được.
        user.setVerificationTokenHash(null);
        user.setVerificationTokenExpiresAt(null);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void resendVerificationEmail(String email) {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Không tìm thấy tài khoản với email này"));

        if (user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tài khoản đã được xác thực. Bạn có thể đăng nhập.");
        }

        String rawToken = assignVerificationToken(user);
        userRepository.save(user);

        emailService.sendVerificationEmail(user.getEmail(), user.getFullName(), rawToken);
    }

    // ─── Quên mật khẩu ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public void forgotPassword(String email) {
        // Không ném lỗi khi email không tồn tại: response giống hệt nhau để người ngoài
        // không dò được email nào đã đăng ký. User gõ nhầm cũng chỉ thấy "đã gửi".
        userRepository.findByEmailAndDeletedFalse(email).ifPresent(user -> {
            if (user.isBanned()) {
                return;
            }
            String rawToken = assignResetToken(user);
            userRepository.save(user);
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), rawToken);
        });
    }

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        User user = userRepository.findByResetTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Link đặt lại mật khẩu không hợp lệ hoặc đã được sử dụng"));

        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Link đặt lại mật khẩu đã hết hạn. Vui lòng yêu cầu gửi lại.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // Token dùng một lần
        user.setResetTokenHash(null);
        user.setResetTokenExpiresAt(null);
        userRepository.save(user);

        // Mật khẩu đổi thì mọi phiên cũ phải chết — kể cả phiên của kẻ đã chiếm được tài khoản.
        revokeAllActiveTokens(user.getId());
    }

    // ─── Login ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailAndDeletedFalse(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        }

        // Check sau password match
        if (user.isBanned()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Tài khoản đã bị khoá");
        }

        // Chưa xác thực email → không cấp token. Mã lỗi riêng để FE hiện nút gửi lại mail.
        if (!user.isEmailVerified()) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Tài khoản chưa được xác thực email. Vui lòng kiểm tra hộp thư và bấm vào link xác thực.");
        }

        return issueTokens(user);
    }

    // ─── Refresh ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token không hợp lệ"));

        if (stored.isRevoked()) {
            // Thu hồi TOÀN BỘ token đang sống của user này, ép đăng nhập lại từ đầu.
            if (stored.getUser() != null) {
                revokeAllActiveTokens(stored.getUser().getId());
            }
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token đã bị thu hồi");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token đã hết hạn");
        }
        if (stored.getUser() == null || stored.getUser().isBanned() || stored.getUser().isDeleted()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token không còn hợp lệ");
        }

        // Revoke token cũ
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        return issueTokens(stored.getUser());
    }

    // ─── Logout ─────────────────────────────────────────────────────────────

    /** Thu hồi mọi refresh token đang sống của user (dùng cho reuse-detection). */
    private void revokeAllActiveTokens(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId).forEach(rt -> {
            rt.setRevokedAt(now);
            refreshTokenRepository.save(rt);
        });
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = sha256Hex(rawRefreshToken);
        // Idempotent: không throw nếu không tìm thấy
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            if (!rt.isRevoked()) {
                rt.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(rt);
            }
        });
    }

    // ─── Change password ────────────────────────────────────────────────────

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));

        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Mật khẩu cũ không đúng");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Thu hồi tất cả refresh token đang hoạt động
        revokeAllActiveTokens(userId);
    }

    // ─── Private helpers ────────────────────────────────────────────────────
    // (getMe đã gom sang UserService.getMe — /auth/me delegate sang đó, bỏ bản sao ở đây)

    /** Token thô 32 byte ngẫu nhiên — chỉ tồn tại trong link email, DB giữ SHA-256. */
    private static String generateRawToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Sinh token xác thực email mới (ghi đè token cũ nếu có), set lên user và trả token thô.
     * Không tự save — caller save một lần cùng các thay đổi khác của user.
     */
    private String assignVerificationToken(User user) {
        String rawToken = generateRawToken();
        user.setVerificationTokenHash(sha256Hex(rawToken));
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(verificationTokenExpiryHours));
        return rawToken;
    }

    /** Như trên nhưng cho luồng đặt lại mật khẩu — hạn ngắn hơn, cột riêng. */
    private String assignResetToken(User user) {
        String rawToken = generateRawToken();
        user.setResetTokenHash(sha256Hex(rawToken));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(resetTokenExpiryMinutes));
        return rawToken;
    }

    /** Tạo cặp access + refresh token, lưu hash refresh vào DB. */
    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String rawRefresh = jwtTokenProvider.generateRefreshToken();

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(sha256Hex(rawRefresh));
        rt.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays));
        refreshTokenRepository.save(rt);

        // Đọc TTL từ provider (cùng nguồn với exp đã ký vào token) — không hardcode, hết lệch khi đổi config
        long expiresInSeconds = jwtTokenProvider.getAccessTokenExpiryMs() / 1000;
        return TokenResponse.of(accessToken, rawRefresh, expiresInSeconds);
    }

    /** SHA-256 hex encode — không dùng thư viện ngoài. */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }
}
