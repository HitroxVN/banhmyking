package com.banhmyking.banhmyking.dto.delivery;

import com.banhmyking.banhmyking.enums.DeliveryArea;
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
@Schema(description = "Kết quả tính toán phí giao hàng")
public class DeliveryFeeResult {

    @Schema(description = "Phí giao hàng thực tế phải trả (đã trừ freeship nếu đạt)", example = "0.00")
    private BigDecimal shippingFee;

    @Schema(description = "Phí giao hàng gốc trước khi xét miễn phí", example = "25000.00")
    private BigDecimal originalFee;

    @Schema(description = "Khoảng cách giao hàng (km) tính toán", example = "3.5")
    private BigDecimal distanceKm;

    @Schema(description = "Phân vùng khu vực giao hàng", example = "INNER_CITY")
    private DeliveryArea area;

    @Schema(description = "Đơn hàng có được miễn phí giao hàng hay không", example = "true")
    private boolean freeship;

    @Schema(description = "Ngưỡng giá trị đơn hàng để được miễn phí giao hàng", example = "200000.00")
    private BigDecimal freeshipThreshold;

    @Schema(description = "Mô tả chi tiết cách tính phí", example = "Đơn hàng từ 200.000đ được miễn phí giao hàng")
    private String description;
}
