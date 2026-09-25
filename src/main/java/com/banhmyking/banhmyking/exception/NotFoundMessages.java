package com.banhmyking.banhmyking.exception;

/**
 * Message 404 chuẩn hóa — hết copy-paste chuỗi "Người dùng không tồn tại..." ×12,
 * "Không tìm thấy đơn hàng..." ×8 across service.
 */
public final class NotFoundMessages {

    private NotFoundMessages() {}

    public static String userById(Long id) {
        return "Người dùng không tồn tại với ID: " + id;
    }

    public static String orderByCode(String code) {
        return "Không tìm thấy đơn hàng với mã: " + code;
    }
}
