package com.banhmyking.banhmyking.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bước 2 của quên mật khẩu: token trong link + mật khẩu mới. */
public record ResetPasswordRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 100) String newPassword
) {}
