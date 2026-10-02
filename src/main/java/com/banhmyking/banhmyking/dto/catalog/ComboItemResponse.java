package com.banhmyking.banhmyking.dto.catalog;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.Product;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/** Một món lẻ trong combo — dùng ở thực đơn và giỏ hàng. */
@Getter
@Builder
public class ComboItemResponse {
    private Long productId;
    private String name;
    private String imageUrl;
    /** Giá gốc của món lẻ (không phải giá KM). */
    private BigDecimal price;
    /** Số phần trong một combo. */
    private int quantity;

    /** Thành phần sắp theo tên; món lẻ → rỗng. */
    public static List<ComboItemResponse> listOf(Product product) {
        if (product == null || !product.isCombo()) {
            return List.of();
        }
        return product.getComboItems().stream()
                .filter(item -> item.getComponent() != null)
                .sorted(Comparator.comparing((ComboItem item) -> item.getComponent().getName()))
                .map(item -> ComboItemResponse.builder()
                        .productId(item.getComponent().getId())
                        .name(item.getComponent().getName())
                        .imageUrl(item.getComponent().getImageUrl())
                        .price(item.getComponent().getPrice())
                        .quantity(item.getQuantity())
                        .build())
                .toList();
    }
}
