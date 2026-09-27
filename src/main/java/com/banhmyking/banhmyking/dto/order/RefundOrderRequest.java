package com.banhmyking.banhmyking.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
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
@Schema(description = "Yêu cầu hoàn tiền cho đơn đã thu tiền")
public class RefundOrderRequest {

    @NotBlank(message = "Lý do hoàn tiền không được để trống")
    @Size(max = 300, message = "Lý do hoàn tiền tối đa 300 ký tự")
    @Schema(description = "Lý do hoàn tiền", example = "Khách báo chuyển khoản nhầm đơn")
    private String reason;

    @DecimalMin(value = "0", inclusive = false, message = "Số tiền hoàn phải lớn hơn 0")
    @Schema(description = "Số tiền hoàn; để trống = hoàn toàn bộ", example = "115000")
    private BigDecimal amount;
}
