package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.entity.Feedback;

/** Dữ liệu email báo admin có phản hồi mới (EmailServiceImpl tự escape HTML). */
public record FeedbackNotice(
        String typeLabel,
        String subject,
        String senderName,
        String phone,
        String email,
        String storeName,
        String orderCode,
        String content
) {
    public static FeedbackNotice from(Feedback feedback) {
        return new FeedbackNotice(feedback.getType().getLabel(), feedback.getSubject(), feedback.getFullName(),
                feedback.getPhone(), feedback.getEmail(),
                feedback.getStore() == null ? null : feedback.getStore().getName(),
                feedback.getRelatedOrder() == null ? null : feedback.getRelatedOrder().getOrderCode(),
                feedback.getContent());
    }
}
