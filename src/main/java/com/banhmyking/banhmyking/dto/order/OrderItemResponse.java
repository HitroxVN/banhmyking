package com.banhmyking.banhmyking.dto.order;

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
@Schema(description = "Snapshot món ăn trong đơn hàng")
public class OrderItemResponse {

    @Schema(description = "ID dòng món trong đơn hàng", example = "1")
    private Long id;

    @Schema(description = "ID món ăn gốc", example = "1")
    private Long productId;

    @Schema(description = "Tên món ăn snapshot tại thời điểm đặt", example = "Bánh mì Pate Chả Lụa")
    private String productName;

    @Schema(description = "Đơn giá gốc món ăn snapshot tại thời điểm đặt", example = "30000.00")
    private BigDecimal unitPrice;

    @Schema(description = "Số lượng", example = "2")
    private Integer quantity;

    @Schema(description = "Tổng tiền cho dòng món này (kèm options)", example = "76000.00")
    private BigDecimal lineTotal;

    @Schema(description = "Danh sách snapshot tùy chọn (topping) đính kèm")
    @Builder.Default
    private List<OrderItemOptionResponse> options = new ArrayList<>();
}
