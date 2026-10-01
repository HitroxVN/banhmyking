package com.banhmyking.banhmyking.dto.delivery;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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

    /** Toạ độ điểm giao (ghim trên bản đồ). Có toạ độ + quán đã ghim vị trí → server tự tính khoảng cách. */
    @jakarta.validation.constraints.DecimalMin(value = "8.0", message = "Vĩ độ nằm ngoài Việt Nam")
    @jakarta.validation.constraints.DecimalMax(value = "23.5", message = "Vĩ độ nằm ngoài Việt Nam")
    @Schema(description = "Vĩ độ điểm giao (tuỳ chọn)", example = "21.028511")
    private java.math.BigDecimal latitude;

    @jakarta.validation.constraints.DecimalMin(value = "102.0", message = "Kinh độ nằm ngoài Việt Nam")
    @jakarta.validation.constraints.DecimalMax(value = "110.0", message = "Kinh độ nằm ngoài Việt Nam")
    @Schema(description = "Kinh độ điểm giao (tuỳ chọn)", example = "105.804817")
    private java.math.BigDecimal longitude;

    @Schema(description = "Địa chỉ nhận hàng chi tiết để phân loại khu vực", example = "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
    private String shippingAddress;

    @DecimalMin(value = "0", message = "subtotal không được âm")
    @Schema(description = "Giá trị tạm tính các món trong đơn hàng (subtotal, >= 0)", example = "150000.00")
    private BigDecimal subtotal;
}
