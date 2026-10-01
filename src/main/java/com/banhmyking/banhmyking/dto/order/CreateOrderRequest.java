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

    // Không còn nhận distanceKm từ client (khách gửi 0 để được ship rẻ) — server tự tính từ toạ độ.
    // Dùng khi giao tới địa chỉ mới (không có addressId); địa chỉ đã lưu lấy toạ độ trong sổ địa chỉ.
    /** Toạ độ điểm giao (ghim trên bản đồ). Có toạ độ + quán đã ghim vị trí → server tự tính khoảng cách. */
    @jakarta.validation.constraints.DecimalMin(value = "8.0", message = "Vĩ độ nằm ngoài Việt Nam")
    @jakarta.validation.constraints.DecimalMax(value = "23.5", message = "Vĩ độ nằm ngoài Việt Nam")
    @Schema(description = "Vĩ độ điểm giao (tuỳ chọn)", example = "21.028511")
    private java.math.BigDecimal latitude;

    @jakarta.validation.constraints.DecimalMin(value = "102.0", message = "Kinh độ nằm ngoài Việt Nam")
    @jakarta.validation.constraints.DecimalMax(value = "110.0", message = "Kinh độ nằm ngoài Việt Nam")
    @Schema(description = "Kinh độ điểm giao (tuỳ chọn)", example = "105.804817")
    private java.math.BigDecimal longitude;

    @Schema(description = "Ghi chú cho quán hoặc shipper", example = "Giao trước 12h trưa, không ớt")
    private String note;

    @jakarta.validation.constraints.Size(max = 64, message = "idempotencyKey tối đa 64 ký tự")
    @Schema(description = "Khoá chống trùng do client sinh cho mỗi lần bấm Đặt hàng; gửi lại cùng khoá sẽ nhận đúng đơn cũ",
            example = "f47ac10b-58cc-4372-a567-0e02b2c3d479")
    private String idempotencyKey;
}
