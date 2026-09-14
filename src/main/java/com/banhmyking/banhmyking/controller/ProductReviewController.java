package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.review.ProductRatingSummaryResponse;
import com.banhmyking.banhmyking.dto.review.ReviewResponse;
import com.banhmyking.banhmyking.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Product Review", description = "APIs xem đánh giá và điểm số sản phẩm (Public)")
public class ProductReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{id}/reviews")
    @Operation(
            summary = "Xem danh sách đánh giá của sản phẩm (Public)",
            description = "Lấy danh sách các đánh giá của món ăn theo ID sản phẩm, hỗ trợ phân trang chuẩn (PageResponse). Không yêu cầu đăng nhập."
    )
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getProductReviews(
            @Parameter(description = "ID món ăn (Product)", example = "10")
            @PathVariable("id") Long productId,
            @Parameter(description = "Số trang (bắt đầu từ 0)", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,
            @Parameter(description = "Kích thước trang", example = "10")
            @RequestParam(value = "size", defaultValue = "10") int size,
            @Parameter(description = "Sắp xếp theo trường (ví dụ: createdAt,desc)", example = "createdAt,desc")
            @RequestParam(value = "sort", defaultValue = "createdAt,desc") String sort) {

        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && "asc".equalsIgnoreCase(sortParams[1])
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String property = sortParams[0];

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, property));
        PageResponse<ReviewResponse> reviewsPage = reviewService.getReviewsByProduct(productId, pageable);

        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đánh giá sản phẩm thành công", reviewsPage));
    }

    @GetMapping("/{id}/rating")
    @Operation(
            summary = "Xem điểm đánh giá trung bình của sản phẩm (Public)",
            description = "Lấy điểm đánh giá trung bình (AVG rating) và tổng số lượt đánh giá của món ăn. Không yêu cầu đăng nhập."
    )
    public ResponseEntity<ApiResponse<ProductRatingSummaryResponse>> getProductRatingSummary(
            @Parameter(description = "ID món ăn (Product)", example = "10")
            @PathVariable("id") Long productId) {
        ProductRatingSummaryResponse summary = reviewService.getProductRatingSummary(productId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin điểm đánh giá trung bình thành công", summary));
    }
}
