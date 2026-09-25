package com.banhmyking.banhmyking.dto.order;

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
@Schema(description = "Yêu cầu gán shipper cho đơn hàng")
public class AssignShipperRequest {

    @NotNull(message = "shipperId không được để trống")
    @Schema(description = "ID của Shipper được gán", example = "4")
    private Long shipperId;

    @Schema(description = "Ghi chú điều phối", example = "Gán cho shipper Hoàng giao gấp")
    private String note;
}
