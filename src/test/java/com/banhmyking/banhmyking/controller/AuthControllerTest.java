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
    void register_success_returns201() throws Exception {
        when(authService.register(any())).thenReturn(MOCK_TOKEN);

        RegisterRequest req = new RegisterRequest("test@test.com", "Password1!", "Test User", null);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access.token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new BusinessException(ErrorCode.CONFLICT, "Email đã tồn tại"));

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
