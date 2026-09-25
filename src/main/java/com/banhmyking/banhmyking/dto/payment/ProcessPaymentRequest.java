package com.banhmyking.banhmyking.dto.payment;

import com.banhmyking.banhmyking.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
@Schema(description = "Request xử lý thanh toán đơn hàng")
public class ProcessPaymentRequest {

    @NotNull(message = "Phương thức thanh toán không được để trống")
    @Schema(description = "Phương thức thanh toán (COD, BANK_TRANSFER, E_WALLET)", example = "BANK_TRANSFER")
    private PaymentMethod method;

    @Schema(description = "Mã tham chiếu giao dịch cổng thanh toán hoặc số tài khoản", example = "TXN-12345678")
    private String transactionRef;

    @Schema(description = "Cờ mô phỏng thanh toán thất bại để kiểm thử phản hồi lỗi trên UI", example = "false")
    private Boolean simulateFailure;
}
