package com.banhmyking.banhmyking.dto.order;

import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Thông tin chi tiết đơn hàng")
public class OrderResponse {

    @Schema(description = "ID đơn hàng", example = "1")
    private Long id;

    @Schema(description = "Mã đơn hàng định dạng BMK-yyyyMMdd-XXXXX", example = "BMK-20260908-A1B2C")
    private String orderCode;

    @Schema(description = "Trạng thái đơn hàng", example = "PENDING")
    private OrderStatus status;

    @Schema(description = "Tên người nhận snapshot", example = "Nguyễn Văn A")
    private String receiverName;

    @Schema(description = "Số điện thoại nhận hàng snapshot", example = "0901234567")
    private String receiverPhone;

    @Schema(description = "Địa chỉ nhận hàng snapshot", example = "123 Lê Lợi, Phường Bến Nghé, Quận 1, TP.HCM")
    private String shippingAddress;

    @Schema(description = "Tiền tạm tính các món trong đơn", example = "76000.00")
    private BigDecimal subtotal;

    @Schema(description = "Phí giao hàng", example = "15000.00")
    private BigDecimal shippingFee;

    @Schema(description = "Số tiền giảm giá", example = "10000.00")
    private BigDecimal discountAmount;

    @Schema(description = "Tổng số tiền thanh toán (subtotal + shippingFee - discountAmount)", example = "81000.00")
    private BigDecimal total;

    @Schema(description = "Mã khuyến mãi đã áp dụng", example = "BANHMYKING10")
    private String promotionCode;

    @Schema(description = "Phương thức thanh toán", example = "COD")
    private PaymentMethod paymentMethod;

    @Schema(description = "Trạng thái thanh toán", example = "PENDING")
    private PaymentStatus paymentStatus;

    @Schema(description = "Ghi chú đơn hàng", example = "Giao giờ trưa, không ớt")
    private String note;

    @Schema(description = "Thời điểm tạo đơn", example = "2026-09-08T22:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Lý do hủy đơn hàng (nếu có)", example = "Khách hàng đổi ý không muốn mua nữa")
    private String cancelReason;

    @Schema(description = "Thời điểm giao hàng thành công", example = "2026-09-08T23:00:00")
    private LocalDateTime deliveredAt;

    @Schema(description = "ID Shipper được phân công giao đơn (nếu có)", example = "4")
    private Long shipperId;

    @Schema(description = "Tên Shipper được phân công (nếu có)", example = "Tài Xế Giao Hàng")
    private String shipperName;

    @Schema(description = "Số điện thoại Shipper (nếu có)", example = "0906665555")
    private String shipperPhone;

    @Schema(description = "Danh sách snapshot các món ăn và topping đã đặt")
    @Builder.Default
    private List<OrderItemResponse> items = new ArrayList<>();
}
