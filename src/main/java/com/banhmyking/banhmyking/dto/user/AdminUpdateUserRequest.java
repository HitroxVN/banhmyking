package com.banhmyking.banhmyking.dto.user;

import com.banhmyking.banhmyking.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Request cập nhật thông tin và phân quyền người dùng từ Admin")
public record AdminUpdateUserRequest(
        @Size(min = 2, max = 100, message = "Họ tên từ 2 đến 100 ký tự")
        @Schema(description = "Họ và tên", example = "Trần Thị Thu")
        String fullName,

        @Schema(description = "Số điện thoại liên hệ", example = "0901234567")
        String phone,

        @Schema(description = "Vai trò phân quyền mới (nếu muốn thay đổi)", example = "SHIPPER")
        RoleName role,

        @Schema(description = "Trạng thái khoá tài khoản (true là khoá, false là mở khoá)", example = "false")
        Boolean banned,

        @Schema(description = "Mật khẩu mới (nếu muốn đặt lại mật khẩu cho user)", example = "newPass123")
        String password
) {}
