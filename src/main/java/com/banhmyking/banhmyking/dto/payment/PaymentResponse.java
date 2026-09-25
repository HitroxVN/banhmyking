package com.banhmyking.banhmyking.dto.payment;

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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Thông tin chi tiết giao dịch thanh toán")
public class PaymentResponse {

    @Schema(description = "ID bản ghi thanh toán", example = "1")
    private Long id;

    @Schema(description = "ID đơn hàng", example = "10")
    private Long orderId;

    @Schema(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
    private String orderCode;

    @Schema(description = "Phương thức thanh toán (COD, BANK_TRANSFER, E_WALLET)", example = "COD")
    private PaymentMethod method;

    @Schema(description = "Trạng thái thanh toán (PENDING, PAID, FAILED, REFUNDED)", example = "PENDING")
    private PaymentStatus status;

    @Schema(description = "Số tiền thanh toán", example = "75000.00")
    private BigDecimal amount;

    @Schema(description = "Thời điểm thanh toán thành công", example = "2026-09-08T12:30:00")
    private LocalDateTime paidAt;

    @Schema(description = "Mã giao dịch cổng thanh toán (nếu có)", example = "VNPAY12345678")
    private String gatewayTxnId;

    @Schema(description = "Thời điểm khởi tạo bản ghi thanh toán", example = "2026-09-08T12:00:00")
    private LocalDateTime createdAt;
}
