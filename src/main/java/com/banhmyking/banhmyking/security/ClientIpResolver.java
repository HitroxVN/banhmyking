package com.banhmyking.banhmyking.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IP client cho chống spam + lưu client_ip. Mặc định chỉ tin remoteAddr: header X-Forwarded-For
 * do client tự đặt được nên chỉ đọc khi triển khai sau reverse proxy tin cậy (app.trust-forwarded-for=true).
 * Khi bật, lấy mục PHẢI NHẤT (do proxy của ta nối thêm); các mục bên trái client tự giả được.
 */
@Component
public class ClientIpResolver {

    private static final int MAX_LENGTH = 45;

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${app.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        String ip = null;
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                ip = parts.length == 0 ? null : parts[parts.length - 1].trim();
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }
}
