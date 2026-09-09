package com.banhmyking.banhmyking.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binding cấu hình JWT từ application.properties (prefix "jwt").
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiryMs,
        int refreshTokenExpiryDays
) {}
