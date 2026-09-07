package com.banhmyking.banhmyking.dto.common;

import com.banhmyking.banhmyking.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Envelope lỗi chuẩn
 */
public record ErrorResponse(
        boolean success,
        String message,
        String errorCode,
        Map<String, String> errors,
        LocalDateTime timestamp
) {
    public static ErrorResponse of(String message, ErrorCode errorCode) {
        return new ErrorResponse(false, message, errorCode.name(), null, LocalDateTime.now());
    }

    public static ErrorResponse of(String message, ErrorCode errorCode, Map<String, String> fieldErrors) {
        return new ErrorResponse(false, message, errorCode.name(), fieldErrors, LocalDateTime.now());
    }
}
