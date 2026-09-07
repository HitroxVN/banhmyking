package com.banhmyking.banhmyking.exception;

import lombok.Getter;

/**
 * Nghiệp vụ chặn request: hết hàng, mã giảm hết lượt, huỷ đơn sai trạng thái…
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
