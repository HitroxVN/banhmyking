package com.banhmyking.banhmyking.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu hủy đơn hàng")
public class CancelOrderRequest {

    @Size(max = 300, message = "Lý do hủy không được vượt quá 300 ký tự")
    @Schema(description = "Lý do hủy đơn hàng (Bắt buộc đối với Staff/Admin)", example = "Khách yêu cầu hủy qua điện thoại")
    private String cancelReason;
}
