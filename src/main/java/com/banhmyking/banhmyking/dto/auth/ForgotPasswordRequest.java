package com.banhmyking.banhmyking.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Bước 1 của quên mật khẩu: nhập email để nhận link đặt lại. */
public record ForgotPasswordRequest(
        @NotBlank @Email String email
) {}
