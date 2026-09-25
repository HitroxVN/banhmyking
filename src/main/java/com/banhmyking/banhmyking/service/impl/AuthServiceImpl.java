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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-expiry-days}")
    private int refreshTokenExpiryDays;

    // ─── Register ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Email đã tồn tại");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        userRepository.save(user);

        return issueTokens(user);
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
        refreshTokenRepository.findByUserIdAndRevokedAtIsNull(userId)
                .forEach(rt -> {
                    rt.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(rt);
                });
    }

    // ─── Private helpers ────────────────────────────────────────────────────
    // (getMe đã gom sang UserService.getMe — /auth/me delegate sang đó, bỏ bản sao ở đây)

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
