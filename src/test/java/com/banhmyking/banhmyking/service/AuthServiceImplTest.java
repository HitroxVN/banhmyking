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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UserRepository userRepository;
    @Mock RefreshTokenRepository refreshTokenRepository;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks AuthServiceImpl authService;

    private void setRefreshExpiry(int days) {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiryDays", days);
    }

    // ─── register ───────────────────────────────────────────────────────────

    @Test
    void register_success() {
        setRefreshExpiry(7);
        RegisterRequest req = new RegisterRequest("a@b.com", "password1", "Nguyen A", null);
        when(userRepository.existsByEmail("a@b.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", 1L);
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("access.token");
        when(jwtTokenProvider.generateRefreshToken()).thenReturn("raw-refresh");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TokenResponse resp = authService.register(req);

        assertThat(resp.accessToken()).isEqualTo("access.token");
        assertThat(resp.refreshToken()).isEqualTo("raw-refresh");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        verify(userRepository).save(any(User.class));
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

    // ─── login ──────────────────────────────────────────────────────────────

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

    // ─── refresh ────────────────────────────────────────────────────────────

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        RefreshToken rt = new RefreshToken();
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
    void refresh_expiredToken_throwsUnauthorized() {
        RefreshToken rt = new RefreshToken();
        rt.setTokenHash("somehash");
        rt.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(rt));

        assertThatThrownBy(() -> authService.refresh("raw-token"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private User buildUser(Long id, String email, String password, boolean deleted) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail(email);
        u.setPassword(password);
        u.setFullName("Test");
        u.setRole(RoleName.CUSTOMER);
        u.setDeleted(deleted);
        return u;
    }
}
