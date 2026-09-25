package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.user.UpdateRoleRequest;
import com.banhmyking.banhmyking.dto.user.UpdateStatusRequest;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock UserService userService;
    @InjectMocks AdminUserController adminUserController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final UserDetailResponse DETAIL = new UserDetailResponse(
            2L, "customer@gmail.com", "Khách Hàng Test", "0901234567",
            null, RoleName.CUSTOMER, false, LocalDateTime.now());

    /** Principal giả — username là userId (JWT subject), authority ADMIN. */
    private static final org.springframework.security.core.userdetails.UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("1")
                    .password("x")
                    .authorities("ROLE_ADMIN")
                    .build();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(adminUserController)
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

    // ─── GET list ───────────────────────────────────────────────────────────

    @Test
    void listUsers_returnsPageResponse() throws Exception {
        when(userService.getUsers(any(), any(), any(), eq(0), eq(10)))
                .thenReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(List.of(DETAIL))));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "0").param("size", "10")
                        .principal(PRINCIPAL::getUsername))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].email").value("customer@gmail.com"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listUsers_sizeCappedAt50() throws Exception {
        when(userService.getUsers(any(), any(), any(), eq(0), eq(50)))
                .thenReturn(PageResponse.from(new org.springframework.data.domain.PageImpl<>(List.of())));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("size", "500")
                        .principal(PRINCIPAL::getUsername))
                .andExpect(status().isOk());

        verify(userService).getUsers(any(), any(), any(), eq(0), eq(50));
    }

    // ─── GET detail ───────────────────────────────────────────────────────────

    @Test
    void getUser_success() throws Exception {
        when(userService.getUser(2L)).thenReturn(DETAIL);

        mockMvc.perform(get("/api/v1/admin/users/2")
                        .principal(PRINCIPAL::getUsername))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.data.banned").value(false));
    }

    @Test
    void getUser_notFound_returns404() throws Exception {
        when(userService.getUser(99L)).thenThrow(
                new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));

        mockMvc.perform(get("/api/v1/admin/users/99")
                        .principal(PRINCIPAL::getUsername))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    // ─── PATCH role ──────────────────────────────────────────────────────────

    @Test
    void changeRole_validBody_callsService() throws Exception {
        when(userService.changeRole(eq(1L), eq(2L), any(UpdateRoleRequest.class)))
                .thenReturn(DETAIL);

        mockMvc.perform(patch("/api/v1/admin/users/2/role")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STAFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).changeRole(eq(1L), eq(2L), any(UpdateRoleRequest.class));
    }

    @Test
    void changeRole_nullRole_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/role")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void changeRole_targetNotFound_returns404() throws Exception {
        when(userService.changeRole(eq(1L), eq(99L), any(UpdateRoleRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy user"));

        mockMvc.perform(patch("/api/v1/admin/users/99/role")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STAFF\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── PATCH status ────────────────────────────────────────────────────────

    @Test
    void changeStatus_validBody_callsService() throws Exception {
        UserDetailResponse banned = new UserDetailResponse(2L, "customer@gmail.com",
                "Khách Hàng Test", "0901234567", null, RoleName.CUSTOMER, true, LocalDateTime.now());
        when(userService.changeStatus(eq(1L), eq(2L), any(UpdateStatusRequest.class)))
                .thenReturn(banned);

        mockMvc.perform(patch("/api/v1/admin/users/2/status")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banned\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.banned").value(true));
    }

    @Test
    void changeStatus_missingBanned_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/2/status")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void changeStatus_lastAdmin_returns400() throws Exception {
        when(userService.changeStatus(eq(1L), eq(2L), any(UpdateStatusRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể khoá ADMIN cuối cùng"));

        mockMvc.perform(patch("/api/v1/admin/users/2/status")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"banned\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể khoá ADMIN cuối cùng"));
    }

    // ─── POST createUser ───────────────────────────────────────────────────────

    @Test
    void createUser_validRequest_success() throws Exception {
        com.banhmyking.banhmyking.dto.user.AdminCreateUserRequest req =
                new com.banhmyking.banhmyking.dto.user.AdminCreateUserRequest(
                        "staff@banhmyking.vn", "123456", "Nguyễn Văn Staff", "0912345678", RoleName.STAFF);

        when(userService.createUser(eq(1L), any(com.banhmyking.banhmyking.dto.user.AdminCreateUserRequest.class)))
                .thenReturn(new UserDetailResponse(
                        10L, "staff@banhmyking.vn", "Nguyễn Văn Staff", "0912345678",
                        null, RoleName.STAFF, false, LocalDateTime.now()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/users")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("staff@banhmyking.vn"))
                .andExpect(jsonPath("$.data.role").value("STAFF"));
    }

    @Test
    void createUser_invalidEmail_returns400() throws Exception {
        String body = "{\"email\":\"not-an-email\",\"password\":\"123456\",\"fullName\":\"Name\",\"role\":\"STAFF\"}";

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/users")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // ─── PUT updateUser ────────────────────────────────────────────────────────

    @Test
    void updateUser_validRequest_success() throws Exception {
        com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest req =
                new com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest(
                        "Nguyễn Cập Nhật", "0987654321", RoleName.SHIPPER, false, null);

        when(userService.updateUser(eq(1L), eq(2L), any(com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest.class)))
                .thenReturn(new UserDetailResponse(
                        2L, "customer@gmail.com", "Nguyễn Cập Nhật", "0987654321",
                        null, RoleName.SHIPPER, false, LocalDateTime.now()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/users/2")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyễn Cập Nhật"))
                .andExpect(jsonPath("$.data.role").value("SHIPPER"));
    }

    @Test
    void updateUser_lastAdminDemotion_returns400() throws Exception {
        com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest req =
                new com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest(
                        "Admin Sửa", "0987654321", RoleName.STAFF, false, null);

        when(userService.updateUser(eq(1L), eq(2L), any(com.banhmyking.banhmyking.dto.user.AdminUpdateUserRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "Không thể hạ quyền ADMIN cuối cùng"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/admin/users/2")
                        .principal(PRINCIPAL::getUsername)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể hạ quyền ADMIN cuối cùng"));
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Test
    void deleteUser_success() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/users/2")
                        .principal(PRINCIPAL::getUsername))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(userService).deleteUser(1L, 2L);
    }

    @Test
    void deleteUser_selfRejected_returns400() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Không thể xoá tài khoản của chính mình"))
                .when(userService).deleteUser(1L, 1L);

        mockMvc.perform(delete("/api/v1/admin/users/1")
                        .principal(PRINCIPAL::getUsername))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Không thể xoá tài khoản của chính mình"));
    }
}
