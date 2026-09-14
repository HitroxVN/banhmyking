package com.banhmyking.banhmyking.dto.order;

import com.banhmyking.banhmyking.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request tạo đơn hàng từ giỏ hàng")
public class CreateOrderRequest {

    @Schema(description = "ID địa chỉ giao hàng đã lưu (nếu để trống, cần nhập 3 trường receiver bên dưới)", example = "1")
    private Long addressId;

    @Schema(description = "Tên người nhận (bắt buộc nếu không chọn addressId)", example = "Nguyễn Văn A")
    private String receiverName;

    @Schema(description = "Số điện thoại người nhận (bắt buộc nếu không chọn addressId)", example = "0901234567")
    private String receiverPhone;

    @Schema(description = "Địa chỉ nhận hàng chi tiết (bắt buộc nếu không chọn addressId)", example = "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
    private String shippingAddress;

    @Schema(description = "Mã khuyến mãi áp dụng (nếu có)", example = "BANHMYKING10")
    private String promotionCode;

    @Schema(description = "Phương thức thanh toán (COD, BANK_TRANSFER, E_WALLET)", example = "COD", defaultValue = "COD")
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.COD;

    // khoảng cách âm không có nghĩa — chặn từ binding (OrderController đã @Valid).
    @jakarta.validation.constraints.DecimalMin(value = "0", message = "distanceKm không được âm")
    @Schema(description = "Khoảng cách giao hàng tính bằng km (tùy chọn, >= 0)", example = "3.5")
    private java.math.BigDecimal distanceKm;

    @Schema(description = "Ghi chú cho quán hoặc shipper", example = "Giao trước 12h trưa, không ớt")
    private String note;
}
