package com.banhmyking.banhmyking.exception;

/**
 * Không tìm thấy resource — trả 404.
 * kế thừa BusinessException(NOT_FOUND) — một cây exception duy nhất, handler bắt
 * BusinessException là đủ; giữ class riêng để call-site/test vẫn đọc rõ ý nghĩa 404.
 */
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }
}
