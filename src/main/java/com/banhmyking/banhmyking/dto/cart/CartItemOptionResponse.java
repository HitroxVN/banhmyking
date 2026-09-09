package com.banhmyking.banhmyking.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Thông tin tùy chọn của món trong giỏ hàng")
public class CartItemOptionResponse {

    @Schema(description = "ID bản ghi cart_item_option", example = "1")
    private Long id;

    @Schema(description = "ID của tùy chọn sản phẩm", example = "1")
    private Long productOptionId;

    @Schema(description = "Tên tùy chọn", example = "Thêm chả lụa")
    private String name;

    @Schema(description = "Giá phụ thu của tùy chọn", example = "8000.00")
    private BigDecimal extraPrice;
}
