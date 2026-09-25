package com.banhmyking.banhmyking.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Body cho /auth/resend-verification. */
public record ResendVerificationRequest(
        @NotBlank @Email String email
) {}
