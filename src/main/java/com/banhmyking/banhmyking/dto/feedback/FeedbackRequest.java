package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.enums.FeedbackType;

/**
 * Gửi phản hồi (spec D §5). orderCode chỉ dùng được khi đăng nhập và là chủ đơn; khi có đơn,
 * cơ sở lấy theo đơn (storeId bị bỏ qua). website là ô bẫy bot.
 */
public record FeedbackRequest(
        FeedbackType type,
        Long storeId,
        String orderCode,
        String fullName,
        String phone,
        String email,
        String subject,
        String content,
        String website
) {
}
