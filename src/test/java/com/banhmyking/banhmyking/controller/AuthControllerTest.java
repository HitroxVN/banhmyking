package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.auth.LoginRequest;
import com.banhmyking.banhmyking.dto.auth.RegisterRequest;
import com.banhmyking.banhmyking.dto.auth.TokenResponse;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock AuthService authService;
    @Mock com.banhmyking.banhmyking.service.UserService userService;
    @InjectMocks AuthController authController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final TokenResponse MOCK_TOKEN =
            TokenResponse.of("access.token", "refresh.token", 900);

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ─── register ───────────────────────────────────────────────────────────

    @Test
    void register_success_returns201WithoutTokens() throws Exception {
        RegisterRequest req = new RegisterRequest("test@test.com", "Password1!", "Test User", null);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản."))
                // chưa xác thực email thì không có token nào được cấp
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // ─── verify email ───────────────────────────────────────────────────────

    @Test
    void verifyEmail_success_returnsSuccessMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã xác thực thành công email."));
    }

    @Test
    void verifyEmail_invalidToken_returns400() throws Exception {
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "Link xác thực không hợp lệ hoặc đã được sử dụng"))
                .when(authService).verifyEmail("bogus");

        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bogus\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    void resendVerification_success_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư."));
    }

    // ─── quên mật khẩu ──────────────────────────────────────────────────────

    @Test
    void forgotPassword_alwaysReturnsSameMessage() throws Exception {
        // Dù email có tồn tại hay không, response phải y hệt nhau
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"test@test.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Nếu email này đã đăng ký, chúng tôi đã gửi link đặt lại mật khẩu. Vui lòng kiểm tra hộp thư."));
    }

    @Test
    void resetPassword_success_returnsSuccessMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\",\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới."));
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "Link đặt lại mật khẩu không hợp lệ"))
                .when(authService).resetPassword("bogus", "NewPass1!");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"bogus\",\"newPassword\":\"NewPass1!\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    void resetPassword_shortNewPassword_returns400() throws Exception {
        // Ràng buộc độ dài mật khẩu nằm ở @Size trên DTO
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        doThrow(new BusinessException(ErrorCode.CONFLICT, "Email đã tồn tại"))
                .when(authService).register(any());

        RegisterRequest req = new RegisterRequest("dup@test.com", "Password1!", "Test User", null);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("CONFLICT"));
    }

    @Test
    void register_invalidBody_returns400() throws Exception {
        String body = "{\"email\":\"\",\"password\":\"Password1!\",\"fullName\":\"A\"}";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── login ──────────────────────────────────────────────────────────────

    @Test
    void login_success_returns200() throws Exception {
        when(authService.login(any())).thenReturn(MOCK_TOKEN);

        LoginRequest req = new LoginRequest("test@test.com", "Password1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh.token"));
    }

    @Test
    void login_unverifiedEmail_returns403WithDedicatedCode() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED, "Tài khoản chưa được xác thực email."));

        LoginRequest req = new LoginRequest("test@test.com", "Password1!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));

        LoginRequest req = new LoginRequest("test@test.com", "wrong");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    }
}
