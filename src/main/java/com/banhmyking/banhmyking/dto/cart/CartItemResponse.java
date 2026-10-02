package com.banhmyking.banhmyking.dto.cart;

import com.banhmyking.banhmyking.dto.catalog.ComboItemResponse;
import com.banhmyking.banhmyking.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Chi tiết một món trong giỏ hàng")
public class CartItemResponse {

    @Schema(description = "ID dòng món trong giỏ", example = "1")
    private Long id;

    @Schema(description = "ID món ăn", example = "1")
    private Long productId;

    @Schema(description = "Tên món ăn", example = "Bánh mì Pate Chả Lụa")
    private String productName;

    @Schema(description = "URL hình ảnh món ăn", example = "https://example.com/banh-mi.jpg")
    private String productImageUrl;

    @Schema(description = "SINGLE | COMBO", example = "SINGLE")
    private ProductType productType;

    @Schema(description = "Giá đang áp dụng của 1 phần (giá KM nếu đang KM, giá combo), chưa gồm topping",
            example = "25000.00")
    private BigDecimal basePrice;

    @Schema(description = "Giá gốc 1 phần chưa gồm topping: món lẻ = giá niêm yết, combo = tổng giá lẻ thành phần",
            example = "30000.00")
    private BigDecimal originalUnitPrice;

    @Schema(description = "Thành phần combo (rỗng với món lẻ)")
    @Builder.Default
    private List<ComboItemResponse> comboItems = new ArrayList<>();

    @Schema(description = "Số lượng", example = "2")
    private Integer quantity;

    @Schema(description = "Đơn giá (giá gốc + phụ thu options)", example = "38000.00")
    private BigDecimal unitPrice;

    @Schema(description = "Tạm tính cho dòng món này (đơn giá x số lượng)", example = "76000.00")
    private BigDecimal subtotal;

    @Schema(description = "Danh sách tùy chọn đã chọn")
    @Builder.Default
    private List<CartItemOptionResponse> options = new ArrayList<>();
}
