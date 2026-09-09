package com.banhmyking.banhmyking.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request thêm món vào giỏ hàng")
public class AddToCartRequest {

    @NotNull(message = "ID món ăn không được để trống")
    @Schema(description = "ID món ăn", example = "1")
    private Long productId;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải ít nhất là 1")
    @Schema(description = "Số lượng đặt", example = "1", defaultValue = "1")
    private Integer quantity;

    @Schema(description = "Danh sách ID tùy chọn (topping/size) đính kèm", example = "[1, 2]")
    @Builder.Default
    private List<Long> optionIds = new ArrayList<>();
}
