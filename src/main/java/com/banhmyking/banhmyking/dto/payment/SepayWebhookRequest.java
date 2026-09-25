package com.banhmyking.banhmyking.dto.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Payload nhận từ Webhook của cổng SePay (tự động nhận biến động số dư ngân hàng)")
public class SepayWebhookRequest {

    @Schema(description = "ID giao dịch trên hệ thống SePay", example = "92704")
    private Long id;

    @Schema(description = "Tên ngân hàng", example = "Techcombank")
    private String gateway;

    @Schema(description = "Thời gian giao dịch trên ngân hàng", example = "2026-09-14 12:35:00")
    private String transactionDate;

    @Schema(description = "Số tài khoản nhận tiền", example = "8888332999")
    private String accountNumber;

    @Schema(description = "Mã nhận diện rút gọn (nếu có)", example = "MB123")
    private String code;

    @Schema(description = "Nội dung chuyển khoản (chứa mã đơn hàng)", example = "BMK202609149W6BQ thanh toan banh my")
    private String content;

    @Schema(description = "Loại giao dịch: 'in' là tiền vào, 'out' là tiền ra", example = "in")
    private String transferType;

    @Schema(description = "Số tiền giao dịch", example = "47000")
    private BigDecimal transferAmount;

    @Schema(description = "Số dư tài khoản sau khi nhận tiền", example = "1904000")
    private BigDecimal accumulated;

    @Schema(description = "Tài khoản phụ (nếu có)", example = "null")
    private String subAccount;

    @Schema(description = "Mã tham chiếu ngân hàng (FT... của Techcombank)", example = "FT26258012345678")
    private String referenceCode;

    @Schema(description = "Chi tiết nội dung giao dịch từ ngân hàng", example = "Techcombank Mobile...")
    private String description;
}
