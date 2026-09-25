package com.banhmyking.banhmyking.dto.promotion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ValidatePromotionRequest {

    @NotBlank(message = "Mã khuyến mãi không được để trống")
    private String code;

    @NotNull(message = "Giá trị đơn hàng không được để trống")
    @DecimalMin(value = "0.0", message = "Giá trị đơn hàng không được nhỏ hơn 0")
    private BigDecimal orderAmount;

    private Long userId;

    /**
     * Phí giao hàng dự kiến — chỉ cần cho mã FREE_SHIP (số tiền được giảm không vượt quá phí ship).
     * Bỏ trống thì dùng mức mặc định của {@link com.banhmyking.banhmyking.service.PriceCalculator}.
     */
    @DecimalMin(value = "0.0", message = "Phí giao hàng không được nhỏ hơn 0")
    private BigDecimal shippingFee;
}
