package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.user.UpdateProfileRequest;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock UserService userService;
    @InjectMocks UserController userController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final UserDetailResponse DETAIL = new UserDetailResponse(
            5L, "customer@gmail.com", "Khách Hàng Test", "0901234567",
            "https://cdn.banhmyking.vn/a/5.png", RoleName.CUSTOMER, false, LocalDateTime.now());

    /** Principal giả — username là userId (JWT subject). */
    private static final org.springframework.security.core.userdetails.UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("5")
                    .password("x")
                    .authorities("ROLE_CUSTOMER")
                    .build();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        // @AuthenticationPrincipal resolve từ SecurityContextHolder, không phải request principal
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMe_returnsDetail() throws Exception {
        when(userService.getMe(5L)).thenReturn(DETAIL);

        mockMvc.perform(get("/api/v1/users/me")
                        .principal(PRINCIPAL::getUsername))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.email").value("customer@gmail.com"))
                .andExpect(jsonPath("$.data.banned").value(false))
                .andExpect(jsonPath("$.data.image").value("https://cdn.banhmyking.vn/a/5.png"))
                .andExpect(jsonPath("$.data.createdAt").exists());
    }

    @Test
    void updateProfile_validBody_callsService() throws Exception {
        when(userService.updateProfile(eq(5L), any(UpdateProfileRequest.class))).thenReturn(DETAIL);

        mockMvc.perform(patch("/api/v1/users/me")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Tên Mới\",\"phone\":\"0912345678\",\"imageUrl\":\"https://x.com/a.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).updateProfile(eq(5L), any(UpdateProfileRequest.class));
    }

    @Test
    void updateProfile_blankFullName_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"phone\":\"0912345678\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
