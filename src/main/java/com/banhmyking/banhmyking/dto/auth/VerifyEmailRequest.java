package com.banhmyking.banhmyking.dto.auth;

import jakarta.validation.constraints.NotBlank;

/** Token thô lấy từ query string trên link trong email xác thực. */
public record VerifyEmailRequest(
        @NotBlank String token
) {}
