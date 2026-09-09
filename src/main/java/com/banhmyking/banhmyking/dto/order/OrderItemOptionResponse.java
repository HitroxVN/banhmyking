package com.banhmyking.banhmyking.dto.order;

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
@Schema(description = "Snapshot tùy chọn (topping) của món trong đơn hàng")
public class OrderItemOptionResponse {

    @Schema(description = "ID bản ghi order_item_option", example = "1")
    private Long id;

    @Schema(description = "Tên tùy chọn snapshot tại thời điểm đặt", example = "Thêm chả lụa")
    private String optionName;

    @Schema(description = "Giá phụ thu snapshot tại thời điểm đặt", example = "8000.00")
    private BigDecimal optionPrice;
}
