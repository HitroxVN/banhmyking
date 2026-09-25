package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.review.CreateReviewRequest;
import com.banhmyking.banhmyking.dto.review.ReviewResponse;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Review", description = "APIs đánh giá món ăn (Customer)")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @Operation(
            summary = "Gửi đánh giá món ăn",
            description = "Gửi đánh giá món ăn trong đơn hàng của khách hàng. Yêu cầu đơn hàng ở trạng thái DELIVERED, xác thực ownership và mỗi order_item chỉ được đánh giá 1 lần."
    )
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody CreateReviewRequest request) {
        // Danh tính CHỈ lấy từ JWT (fail-closed) — không dùng header người gọi tự khai
        Long userId = SecurityUtils.requireUserId(principal);
        ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi đánh giá thành công", response));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Operation(
            summary = "Danh sách đánh giá (STAFF, ADMIN)",
            description = "Dùng cho trang quản lý đánh giá. Lọc theo món và/hoặc số sao; bỏ trống tham số = lấy tất cả."
    )
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getAllReviews(
            @Parameter(description = "Lọc theo ID món ăn", example = "1")
            @RequestParam(value = "productId", required = false) Long productId,
            @Parameter(description = "Lọc theo số sao (1–5)", example = "5")
            @RequestParam(value = "rating", required = false) Integer rating,
            @Parameter(description = "Số trang (bắt đầu từ 0)", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<ReviewResponse> reviews = reviewService.getAllReviews(productId, rating, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đánh giá thành công", reviews));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Xoá đánh giá (ADMIN)",
            description = "Xoá hẳn đánh giá. Khách sẽ được đánh giá lại món đó và điểm sao của món được tính lại ngay."
    )
    public ResponseEntity<ApiResponse<Void>> deleteReview(
            @Parameter(description = "ID đánh giá", example = "1")
            @PathVariable("id") Long reviewId) {
        reviewService.deleteReview(reviewId);
        return ResponseEntity.ok(ApiResponse.ok("Xoá đánh giá thành công"));
    }
}
