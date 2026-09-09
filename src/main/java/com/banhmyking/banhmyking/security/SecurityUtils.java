package com.banhmyking.banhmyking.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Lấy ID người dùng hiện tại từ SecurityContext (được trích xuất từ JWT token hoặc X-User-Id).
     * Nếu không có xác thực hợp lệ, fallback về headerUserId hoặc mặc định 1L (dành cho test Swagger).
     */
    public static Long resolveUserId(Long headerUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            try {
                return Long.parseLong(authentication.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        return headerUserId != null ? headerUserId : 1L;
    }
}
