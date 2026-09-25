package com.banhmyking.banhmyking.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request báo cáo giao hàng thất bại của Shipper")
public class FailDeliveryRequest {

    @NotBlank(message = "Lý do giao hàng thất bại không được để trống")
    @Schema(description = "Lý do giao hàng không thành công (vd: Khách không nghe máy, sai địa chỉ...)", example = "Khách hàng không nhấc máy sau 3 lần gọi")
    private String reason;
}
