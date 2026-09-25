package com.banhmyking.banhmyking.dto.order;

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
@Schema(description = "Yêu cầu xác nhận giao hàng")
public class ConfirmDeliveryRequest {

    @Schema(description = "Ghi chú khi giao hàng", example = "Đã giao tận tay người nhận")
    private String note;
}
