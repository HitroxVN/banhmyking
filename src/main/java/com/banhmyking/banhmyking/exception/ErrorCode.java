package com.banhmyking.banhmyking.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Mã lỗi chuẩn toàn app — string tự do sẽ có 4 cách viết "not_found".
 */
@Getter
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    /** Đúng mật khẩu nhưng chưa click link xác thực email — FE dựa vào mã này để hiện nút gửi lại. */
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
}
