package com.banhmyking.banhmyking.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cập nhật hồ sơ cá nhân. */
@Schema(description = "Request cập nhật hồ sơ cá nhân")
public record UpdateProfileRequest(
        @NotBlank(message = "Họ tên không được để trống")
        @Size(max = 100, message = "Họ tên tối đa 100 ký tự")
        @Schema(description = "Họ tên mới", example = "Nguyễn Văn A") String fullName,

        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        @Schema(description = "Số điện thoại mới", example = "0901234567") String phone,

        @Size(max = 255, message = "URL ảnh tối đa 255 ký tự")
        @Schema(description = "URL ảnh đại diện (frontend tự host)", example = "https://localhost/a/1.png")
        String imageUrl) {
}
