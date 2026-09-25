package com.banhmyking.banhmyking.dto.user;

import com.banhmyking.banhmyking.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** Thông tin user đầy đủ — dùng cho /users/me và phần admin quản lý user. */
@Schema(description = "Thông tin chi tiết người dùng")
public record UserDetailResponse(
        @Schema(description = "ID người dùng") Long id,
        @Schema(description = "Email") String email,
        @Schema(description = "Họ tên") String fullName,
        @Schema(description = "Số điện thoại") String phone,
        @Schema(description = "URL ảnh đại diện") String image,
        @Schema(description = "Vai trò") RoleName role,
        @Schema(description = "Đã bị khoá?") boolean banned,
        @Schema(description = "Thời điểm tạo tài khoản") LocalDateTime createdAt) {
}
