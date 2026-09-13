package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.user.UpdateProfileRequest;
import com.banhmyking.banhmyking.dto.user.UserDetailResponse;
import com.banhmyking.banhmyking.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "APIs hồ sơ cá nhân (mọi user đã đăng nhập)")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Xem hồ sơ của tôi", description = "Thông tin đầy đủ của user hiện tại (kèm trạng thái khoá, avatar).")
    public ApiResponse<UserDetailResponse> getMe(
            @AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok("Lấy thông tin thành công", userService.getMe(userId(principal)));
    }

    @PatchMapping("/me")
    @Operation(summary = "Cập nhật hồ sơ của tôi", description = "Sửa họ tên, số điện thoại, avatar (URL). Email không thể đổi.")
    public ApiResponse<UserDetailResponse> updateProfile(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("Cập nhật hồ sơ thành công", userService.updateProfile(userId(principal), request));
    }

    /** JWT subject = userId → username chính là userId. */
    private Long userId(UserDetails principal) {
        return Long.valueOf(principal.getUsername());
    }
}
