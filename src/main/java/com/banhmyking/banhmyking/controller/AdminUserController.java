package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.user.UpdateRoleRequest;
import com.banhmyking.banhmyking.dto.user.UpdateStatusRequest;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "Admin Users", description = "APIs quản lý người dùng (ADMIN toàn quyền, STAFF chỉ đọc)")
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách người dùng",
            description = "Phân trang + filter theo role, trạng thái khoá, từ khoá (email/họ tên). STAFF chỉ đọc được, ADMIN toàn quyền.")
    public ApiResponse<PageResponse<UserDetailResponse>> getUsers(
            @Parameter(description = "Lọc theo vai trò") @RequestParam(required = false) RoleName role,
            @Parameter(description = "Lọc theo trạng thái khoá") @RequestParam(required = false) Boolean banned,
            @Parameter(description = "Từ khoá tìm email/họ tên") @RequestParam(required = false) String keyword,
            @Parameter(description = "Trang (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử/trang (tối đa 50)") @RequestParam(defaultValue = "10") int size) {

        return ApiResponse.ok("Lấy danh sách người dùng thành công",
                userService.getUsers(role, banned, keyword, page, Math.min(size, 50)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết người dùng", description = "Xem thông tin chi tiết 1 user theo ID.")
    public ApiResponse<UserDetailResponse> getUser(@PathVariable Long id) {
        return ApiResponse.ok("Lấy thông tin người dùng thành công", userService.getUser(id));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Đổi vai trò", description = "ADMIN đổi vai trò 1 user. Tự động thu hồi refresh token của user đó.")
    public ApiResponse<UserDetailResponse> changeRole(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Đổi vai trò thành công",
                userService.changeRole(actorId(principal), id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khoá/mở tài khoản", description = "ADMIN khoá (banned=true) hoặc mở khoá (banned=false). Khoá sẽ thu hồi refresh token + chặn token cũ.")
    public ApiResponse<UserDetailResponse> changeStatus(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ApiResponse.ok("Cập nhật trạng thái tài khoản thành công",
                userService.changeStatus(actorId(principal), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xoá người dùng", description = "Soft-delete (is_deleted=true) + xoá sạch refresh token.")
    public ApiResponse<Void> deleteUser(
            @AuthenticationPrincipal UserDetails principal,
            @PathVariable Long id) {
        userService.deleteUser(actorId(principal), id);
        return ApiResponse.ok("Xoá người dùng thành công");
    }

    /** JWT subject = userId → username chính là userId. */
    private Long actorId(UserDetails principal) {
        return SecurityUtils.requireUserId(principal);
    }
}
