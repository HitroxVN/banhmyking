package com.banhmyking.banhmyking.dto.cart;

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
@Schema(description = "Thông tin giỏ hàng và tạm tính")
public class CartResponse {

    @Schema(description = "ID giỏ hàng (null nếu chưa có dòng trong DB)", example = "1")
    private Long cartId;

    @Schema(description = "Danh sách món trong giỏ")
    @Builder.Default
    private List<CartItemResponse> items = new ArrayList<>();

    @Schema(description = "Tổng số lượng sản phẩm trong giỏ", example = "3")
    private int totalQuantity;

    @Schema(description = "Tổng tiền tạm tính (toàn bộ ở server)", example = "114000.00")
    private BigDecimal subtotal;

    public static CartResponse empty() {
        return CartResponse.builder()
                .cartId(null)
                .items(new ArrayList<>())
                .totalQuantity(0)
                .subtotal(BigDecimal.ZERO)
                .build();
    }
}
