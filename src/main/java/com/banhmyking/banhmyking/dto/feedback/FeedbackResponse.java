package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import java.time.LocalDateTime;

/** Phản hồi cho màn quản lý. storeId null = chung toàn chuỗi. */
public record FeedbackResponse(
        Long id,
        FeedbackType type,
        Long storeId,
        String storeName,
        String orderCode,
        Long userId,
        String fullName,
        String phone,
        String email,
        String subject,
        String content,
        FeedbackStatus status,
        String resolutionNote,
        String handledByName,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {
    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getType(),
                feedback.getStore() == null ? null : feedback.getStore().getId(),
                feedback.getStore() == null ? null : feedback.getStore().getName(),
                feedback.getRelatedOrder() == null ? null : feedback.getRelatedOrder().getOrderCode(),
                feedback.getUser() == null ? null : feedback.getUser().getId(),
                feedback.getFullName(), feedback.getPhone(), feedback.getEmail(), feedback.getSubject(),
                feedback.getContent(), feedback.getStatus(), feedback.getResolutionNote(),
                feedback.getHandledBy() == null ? null : feedback.getHandledBy().getFullName(),
                feedback.getHandledAt(), feedback.getCreatedAt());
    }
}
