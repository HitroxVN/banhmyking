package com.banhmyking.banhmyking.dto.user;

import com.banhmyking.banhmyking.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request tạo người dùng mới từ trang Quản trị Admin")
public record AdminCreateUserRequest(
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng")
        @Schema(description = "Email người dùng", example = "staff01@banhmyking.vn")
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
        @Schema(description = "Mật khẩu đăng nhập", example = "123456")
        String password,

        @NotBlank(message = "Họ và tên không được để trống")
        @Size(min = 2, max = 100, message = "Họ tên từ 2 đến 100 ký tự")
        @Schema(description = "Họ và tên đầy đủ", example = "Trần Thị Thu")
        String fullName,

        @Schema(description = "Số điện thoại liên hệ", example = "0901234567")
        String phone,

        @NotNull(message = "Vai trò không được để trống")
        @Schema(description = "Vai trò phân quyền (STAFF, SHIPPER, CUSTOMER, ADMIN)", example = "STAFF")
        RoleName role
) {}
