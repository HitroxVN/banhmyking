package com.banhmyking.banhmyking.dto.store;

import com.banhmyking.banhmyking.enums.ProductType;
import java.math.BigDecimal;
import java.util.List;
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
    /** SINGLE | COMBO — combo không có tồn riêng, chỉ bật/tắt. */
    private ProductType productType;
    /** Món còn trong thực đơn chuỗi (admin tắt = cả chuỗi ngừng bán). */
    private boolean onChainMenu;
    /** Cơ sở đang bán món (không đánh dấu hết món). */
    private boolean available;
    /** NULL = không quản tồn tại cơ sở (combo luôn NULL). */
    private Integer stockQuantity;
    private int lowStockThreshold;
    private boolean lowStock;
    /** Thành phần đang làm combo không bán được tại cơ sở; rỗng với món lẻ. */
    private List<String> blockedBy;
}
