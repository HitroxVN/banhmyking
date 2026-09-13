package com.banhmyking.banhmyking.dto.delivery;

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
@Schema(description = "Yêu cầu tính thử phí giao hàng")
public class CalculateDeliveryFeeRequest {

    @Schema(description = "Khoảng cách giao hàng tính bằng km (tùy chọn)", example = "3.5")
    private BigDecimal distanceKm;

    @Schema(description = "Địa chỉ nhận hàng chi tiết để phân loại khu vực", example = "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
    private String shippingAddress;

    @Schema(description = "Giá trị tạm tính các món trong đơn hàng (subtotal)", example = "150000.00")
    private BigDecimal subtotal;
}
