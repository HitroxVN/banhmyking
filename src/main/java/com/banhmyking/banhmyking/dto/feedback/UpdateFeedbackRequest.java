package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.enums.FeedbackStatus;

/** PATCH phản hồi: trường null = giữ nguyên; resolutionNote rỗng = xoá ghi chú. */
public record UpdateFeedbackRequest(FeedbackStatus status, String resolutionNote) {
}
