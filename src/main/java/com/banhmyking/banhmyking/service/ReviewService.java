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

    /**
     * Danh sách đánh giá cho trang quản lý (STAFF & ADMIN).
     * @param productId lọc theo món, {@code null} = tất cả
     * @param rating   lọc theo số sao (1–5), {@code null} = tất cả
     */
    PageResponse<ReviewResponse> getAllReviews(Long productId, Integer rating, Pageable pageable);

    /**
     * Xoá hẳn một đánh giá (chỉ ADMIN — chốt quyền ở controller).
     * Lưu ý: xoá xong khách được đánh giá lại món đó và điểm sao của món tính lại ngay.
     */
    void deleteReview(Long reviewId);
}
