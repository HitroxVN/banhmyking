package com.banhmyking.banhmyking.security;

import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import org.springframework.security.core.userdetails.UserDetails;

public class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Lấy userId từ JWT principal (subject = userId). Fail-closed:
     * không có authentication hợp lệ → 401, không bao giờ fallback về id khác.
     */
    public static Long requireUserId(UserDetails principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Chưa xác thực: thiếu token hợp lệ");
        }
        try {
            return Long.valueOf(principal.getUsername());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Token không chứa userId hợp lệ");
        }
    }
}
