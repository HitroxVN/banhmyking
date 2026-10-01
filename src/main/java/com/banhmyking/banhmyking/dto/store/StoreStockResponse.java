package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** Một món trong trang "Tình trạng món" của một cơ sở. */
@Getter
@Builder
public class StoreStockResponse {
    private Long productId;
    private String productName;
    private String categoryName;
    private String imageUrl;
    private BigDecimal price;
    /** Món còn trong thực đơn chuỗi (admin tắt = cả chuỗi ngừng bán). */
    private boolean onChainMenu;
    /** Cơ sở đang bán món (không đánh dấu hết món). */
    private boolean available;
    /** NULL = không quản tồn tại cơ sở. */
    private Integer stockQuantity;
    private int lowStockThreshold;
    private boolean lowStock;
}
