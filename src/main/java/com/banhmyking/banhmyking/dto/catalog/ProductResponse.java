package com.banhmyking.banhmyking.dto.catalog;

import java.math.BigDecimal;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {
    private Long id;
    private Long categoryId;
    private String categoryName;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private boolean available;
    private boolean featured;
    /** Điểm trung bình cộng của các đánh giá (0.0 khi chưa có đánh giá nào) */
    private Double averageRating;
    private Long totalReviews;
    private List<ProductOptionResponse> options;
}
