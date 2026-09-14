package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.review.CreateReviewRequest;
import com.banhmyking.banhmyking.dto.review.ReviewResponse;
import com.banhmyking.banhmyking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @Valid @RequestBody CreateReviewRequest request) {
        Long userId = resolveUserId(headerUserId);
        ReviewResponse response = reviewService.createReview(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Gửi đánh giá thành công", response));
    }

    private Long resolveUserId(Long headerUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Long id) {
                return id;
            } else if (principal instanceof String s) {
                try {
                    return Long.valueOf(s);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return headerUserId != null ? headerUserId : 1L;
    }
}
