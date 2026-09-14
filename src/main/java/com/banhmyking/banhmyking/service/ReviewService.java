package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.review.CreateReviewRequest;
import com.banhmyking.banhmyking.dto.review.ReviewResponse;

public interface ReviewService {

    /**
     * Tạo đánh giá (review) cho một món ăn trong đơn hàng.
     * Đảm bảo đơn hàng ở trạng thái DELIVERED, kiểm tra ownership và duy nhất theo orderItem.
     */
    ReviewResponse createReview(Long userId, CreateReviewRequest request);
}
