package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.auth.LoginRequest;
import com.banhmyking.banhmyking.dto.auth.RegisterRequest;
import com.banhmyking.banhmyking.dto.auth.TokenResponse;
import com.banhmyking.banhmyking.entity.RefreshToken;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.RefreshTokenRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import com.banhmyking.banhmyking.service.impl.AuthServiceImpl;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;

    @InjectMocks AuthServiceImpl authService;

    private void setRefreshExpiry(int days) {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", days);
    }

    private void setVerificationExpiry(int hours) {
        ReflectionTestUtils.setField(authService, "verificationTokenExpiryHours", hours);
    }

    private void setResetExpiry(int minutes) {
        ReflectionTestUtils.setField(authService, "resetTokenExpiryMinutes", minutes);
    }

    // ─── register ───────────────────────────────────────────────────────────

    @Test
    void register_savesUnverifiedUser_andSendsVerificationEmail() {
        setVerificationExpiry(24);
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("a@b.com", "password1", "Nguyen A", null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().isEmailVerified()).isFalse();
        assertThat(saved.getValue().getVerificationTokenHash()).isNotBlank();
        assertThat(saved.getValue().getVerificationTokenExpiresAt()).isAfter(LocalDateTime.now());

        // Token thô trong mail phải khác hash lưu DB
        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq("a@b.com"), eq("Nguyen A"), rawToken.capture());
        assertThat(rawToken.getValue()).isNotBlank();
        assertThat(rawToken.getValue()).isNotEqualTo(saved.getValue().getVerificationTokenHash());
    }

    @Test
    void register_doesNotIssueTokens() {
        setVerificationExpiry(24);
        when(userRepository.existsByEmail("x@y.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register(new RegisterRequest("x@y.com", "password1", "Nguyen X", null));

        // Tài khoản chưa xác thực không được cấp token nào
        verify(refreshTokenRepository, never()).save(any());
        verify(jwtTokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        RegisterRequest req = new RegisterRequest("dup@b.com", "password1", "Nguyen B", null);
        when(userRepository.existsByEmail("dup@b.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        verify(userRepository, never()).save(any());
    }

    // ─── verifyEmail ────────────────────────────────────────────────────────

    @Test
    void verifyEmail_validToken_marksVerifiedAndClearsToken() {
        User user = buildUser(1L, "x@y.com", "hashed", false);
        user.setEmailVerified(false);
        user.setVerificationTokenHash(hashOf("raw-token"));
        user.setVerificationTokenExpiresAt(LocalDateTime.now().plusHours(1));
        when(userRepository.findByVerificationTokenHash(anyString())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.verifyEmail("raw-token");

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationTokenHash()).isNull();
        assertThat(user.getVerificationTokenExpiresAt()).isNull();
    }

    @Test
    void verifyEmail_unknownToken_throwsBusinessError() {
        when(userRepository.findByVerificationTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.verifyEmail("bogus"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    void verifyEmail_expiredToken_throwsAndDoesNotVerify() {
        User user = buildUser(1L, "x@y.com", "hashed", false);
        user.setEmailVerified(false);
        user.setVerificationTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByVerificationTokenHash(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.verifyEmail("raw-token"))
                .isInstanceOf(BusinessException.class);

        assertThat(user.isEmailVerified()).isFalse();
        verify(userRepository, never()).save(any());
    }

    // ─── resendVerificationEmail ────────────────────────────────────────────

    @Test
    void resendVerification_unverifiedUser_issuesNewToken() {
        setVerificationExpiry(24);
        User user = buildUser(1L, "x@y.com", "hashed", false);
        user.setEmailVerified(false);
        when(userRepository.findByEmailAndDeletedFalse("x@y.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.resendVerificationEmail("x@y.com");

        verify(emailService).sendVerificationEmail(eq("x@y.com"), anyString(), anyString());
        assertThat(user.getVerificationTokenHash()).isNotBlank();
    }

    @Test
    void resendVerification_alreadyVerified_throwsBusinessError() {
        User user = buildUser(1L, "x@y.com", "hashed", false);
        user.setEmailVerified(true);
        when(userRepository.findByEmailAndDeletedFalse("x@y.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resendVerificationEmail("x@y.com"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);

        verify(emailService, never()).sendVerificationEmail(anyString(), anyString(), anyString());
    }

    // ─── forgotPassword ─────────────────────────────────────────────────────

    @Test
    void forgotPassword_existingEmail_issuesResetTokenAndSendsMail() {
        setResetExpiry(30);
        User user = buildUser(1L, "x@y.com", "hashed", false);
        when(userRepository.findByEmailAndDeletedFalse("x@y.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.forgotPassword("x@y.com");

        assertThat(user.getResetTokenHash()).isNotBlank();
        assertThat(user.getResetTokenExpiresAt()).isAfter(LocalDateTime.now());
        verify(emailService).sendPasswordResetEmail(eq("x@y.com"), anyString(), anyString());
    }

    @Test
    void forgotPassword_unknownEmail_isSilent() {
        // Không được lộ email nào đã đăng ký: không ném lỗi, không gửi mail
        when(userRepository.findByEmailAndDeletedFalse("nobody@x.com")).thenReturn(Optional.empty());

        authService.forgotPassword("nobody@x.com");

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void forgotPassword_bannedUser_doesNotSendMail() {
        User user = buildUser(1L, "locked@y.com", "hashed", false);
        user.setBanned(true);
        when(userRepository.findByEmailAndDeletedFalse("locked@y.com")).thenReturn(Optional.of(user));

        authService.forgotPassword("locked@y.com");

        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString(), anyString());
    }

    // ─── resetPassword ──────────────────────────────────────────────────────

    @Test
    void resetPassword_validToken_changesPasswordAndRevokesSessions() {
        User user = buildUser(7L, "x@y.com", "old-hash", false);
        user.setResetTokenHash(hashOf("raw-token"));
        user.setResetTokenExpiresAt(LocalDateTime.now().plusMinutes(10));
        when(userRepository.findByResetTokenHash(anyString())).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode("NewPass1!")).thenReturn("new-hash");

        RefreshToken active = new RefreshToken();
        active.setUser(user);
        active.setExpiresAt(LocalDateTime.now().plusDays(7));
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(7L)).thenReturn(java.util.List.of(active));

        authService.resetPassword("raw-token", "NewPass1!");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        // Token dùng một lần
        assertThat(user.getResetTokenHash()).isNull();
        assertThat(user.getResetTokenExpiresAt()).isNull();
        // Mọi phiên đang đăng nhập phải chết
        assertThat(active.isRevoked()).isTrue();
    }

    @Test
    void resetPassword_unknownToken_throwsBusinessError() {
        when(userRepository.findByResetTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.resetPassword("bogus", "NewPass1!"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void resetPassword_expiredToken_throwsAndDoesNotChangePassword() {
        User user = buildUser(7L, "x@y.com", "old-hash", false);
        user.setResetTokenHash(hashOf("raw-token"));
        user.setResetTokenExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(userRepository.findByResetTokenHash(anyString())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.resetPassword("raw-token", "NewPass1!"))
                .isInstanceOf(BusinessException.class);

        assertThat(user.getPassword()).isEqualTo("old-hash");
        verify(userRepository, never()).save(any());
    }

    // ─── login ──────────────────────────────────────────────────────────────

    @Test
    void login_unverifiedEmail_throwsEmailNotVerified() {
        User user = buildUser(1L, "x@y.com", "hashed", false);
        user.setEmailVerified(false);
        when(userRepository.findByEmailAndDeletedFalse("x@y.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@y.com", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        setRefreshExpiry(7);
        User user = buildUser(1L, "x@y.com", "hashed", false);
        when(userRepository.findByEmailAndDeletedFalse("x@y.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("x@y.com", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void login_userNotFound_throwsUnauthorized() {
        when(userRepository.findByEmailAndDeletedFalse("no@no.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("no@no.com", "pw")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void login_bannedUser_throwsUnauthorized() {
        setRefreshExpiry(7);
        User user = buildUser(1L, "locked@y.com", "hashed", false);
        user.setBanned(true);
        when(userRepository.findByEmailAndDeletedFalse("locked@y.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("locked@y.com", "pw")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tài khoản đã bị khoá")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void login_expiresInComesFromProviderTtl_notHardcoded() {
        // expiresIn phải khớp TTL access token thực sự được ký (đọc config), không phải 900 cố định
        setRefreshExpiry(7);
        User user = buildUser(1L, "x@y.com", "hashed", false);
        when(userRepository.findByEmailAndDeletedFalse("x@y.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pw", "hashed")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access.token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(jwtTokenProvider.getAccessTokenExpiryMs()).thenReturn(1800000L); // 30 phút
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse resp = authService.login(new LoginRequest("x@y.com", "pw"));

        assertThat(resp.expiresIn()).isEqualTo(1800L);
    }

    // ─── refresh ────────────────────────────────────────────────────────────

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        User user = buildUser(1L, "x@y.com", "hashed", false);
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash("somehash");
        rt.setExpiresAt(LocalDateTime.now().plusDays(7));
        rt.setRevokedAt(LocalDateTime.now().minusHours(1));

        // sha256 của "raw-token" — ta mock findByTokenHash với any
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refresh_reuseOfRevokedToken_revokesAllActiveTokens() {
        // Reuse detection: dùng lại token đã revoke → thu hồi TOÀN BỘ token đang sống của user
        User user = buildUser(1L, "x@y.com", "hashed", false);
        ReflectionTestUtils.setField(user, "id", 1L);
        RefreshToken stolen = new RefreshToken();
        stolen.setUser(user);
        stolen.setTokenHash("somehash");
        stolen.setExpiresAt(LocalDateTime.now().plusDays(7));
        stolen.setRevokedAt(LocalDateTime.now().minusHours(1));

        RefreshToken otherActive = new RefreshToken();
        otherActive.setUser(user);
        otherActive.setTokenHash("otherhash");
        otherActive.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stolen));
        when(refreshTokenRepository.findByUserIdAndRevokedAtIsNull(1L))
                .thenReturn(java.util.List.of(otherActive));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class);

        assertThat(otherActive.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(otherActive);
    }

    @Test
    void refresh_bannedUser_throwsUnauthorized() {
        User user = buildUser(1L, "x@y.com", "hashed", false);
        user.setBanned(true);
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash("somehash");
        rt.setExpiresAt(LocalDateTime.now().plusDays(7));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        RefreshToken rt = new RefreshToken();
        rt.setUser(buildUser(1L, "x@y.com", "hashed", false));
        rt.setTokenHash("somehash");
        rt.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    /** SHA-256 hex — khớp với cách AuthServiceImpl băm token. */
    private static String hashOf(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(
                    digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** User mặc định coi như đã xác thực email — test nào cần chưa xác thực thì set lại false. */
    private User buildUser(Long id, String email, String password, boolean deleted) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail(email);
        u.setPassword(password);
        u.setFullName("Test");
        u.setRole(RoleName.CUSTOMER);
        u.setDeleted(deleted);
        u.setEmailVerified(true);
        return u;
    }
}
