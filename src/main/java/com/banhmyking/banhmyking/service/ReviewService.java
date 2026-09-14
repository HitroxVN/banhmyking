package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.review.CreateReviewRequest;
import com.banhmyking.banhmyking.dto.review.ProductRatingSummaryResponse;
import com.banhmyking.banhmyking.dto.review.ReviewResponse;
import org.springframework.data.domain.Pageable;

public interface ReviewService {

    /**
     * Tạo đánh giá (review) cho một món ăn trong đơn hàng.
     * Đảm bảo đơn hàng ở trạng thái DELIVERED, kiểm tra ownership và duy nhất theo orderItem.
     */
    ReviewResponse createReview(Long userId, CreateReviewRequest request);

    /**
     * Lấy danh sách đánh giá của một món ăn có phân trang (Public).
     */
    PageResponse<ReviewResponse> getReviewsByProduct(Long productId, Pageable pageable);

    /**
     * Lấy thông tin điểm đánh giá trung bình của món ăn (Public).
     */
    ProductRatingSummaryResponse getProductRatingSummary(Long productId);
}
