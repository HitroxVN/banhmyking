package com.banhmyking.banhmyking.dto.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.banhmyking.banhmyking.enums.ProductType;

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
    /** Bộ ảnh chi tiết, đã sắp theo thứ tự hiển thị. */
    private List<String> images;
    /** Giá gốc (giá niêm yết). Giá đang bán xem effectivePrice. */
    private BigDecimal price;
    /** Khách đặt được ở mức chuỗi: món bật; combo còn cần mọi thành phần bật và chưa xoá. */
    private boolean available;
    /** Cờ bật/tắt của CHÍNH sản phẩm (is_available) — nút bật/tắt của admin dùng cờ này. */
    private boolean enabled;
    /** SINGLE | COMBO */
    private ProductType productType;
    /** Giá KM đã cấu hình (kể cả chưa tới hạn / đã hết) — chỉ món lẻ. */
    private BigDecimal salePrice;
    /** Giờ Việt Nam; null = áp dụng ngay. */
    private LocalDateTime saleStartsAt;
    /** Giờ Việt Nam; null = không hết hạn. */
    private LocalDateTime saleEndsAt;
    /** Giá đang bán lúc trả response (server là nguồn sự thật). */
    private BigDecimal effectivePrice;
    /** Giá gạch; null = không gạch giá. */
    private BigDecimal compareAtPrice;
    /** % giảm làm tròn xuống; null nếu không giảm. */
    private Integer discountPercent;
    /** Món lẻ đang trong thời gian KM. */
    private boolean onSale;
    /** Thành phần combo; rỗng với món lẻ. */
    private List<ComboItemResponse> comboItems;
    private boolean featured;
    /** Điểm trung bình cộng của các đánh giá (0.0 khi chưa có đánh giá nào) */
    private Double averageRating;
    private Long totalReviews;
    private List<ProductOptionResponse> options;
    /** Nhóm lựa chọn đã sắp theo {@code sortOrder}; rỗng = món chỉ có option phẳng (dữ liệu cũ). */
    private List<OptionGroupResponse> optionGroups;
}
