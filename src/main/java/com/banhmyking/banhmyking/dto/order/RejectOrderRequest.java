package com.banhmyking.banhmyking.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
@Schema(description = "Yêu cầu từ chối nhận đơn hàng từ phía Shipper")
public class RejectOrderRequest {

    @NotBlank(message = "Lý do từ chối nhận đơn không được để trống")
    @Schema(description = "Lý do tài xế từ chối nhận đơn hàng", example = "Xe gặp sự cố hỏng hóc trên đường")
    private String reason;
}
