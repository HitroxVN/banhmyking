package com.banhmyking.banhmyking.dto.order;

import com.banhmyking.banhmyking.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Yêu cầu cập nhật trạng thái đơn hàng")
public class UpdateOrderStatusRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    @Schema(description = "Trạng thái mới của đơn hàng", example = "CONFIRMED")
    private OrderStatus newStatus;

    @Size(max = 300, message = "Ghi chú không được vượt quá 300 ký tự")
    @Schema(description = "Ghi chú cho lần chuyển trạng thái này", example = "Bếp bắt đầu chế biến")
    private String note;

    @Schema(description = "ID Shipper (tùy chọn khi chuyển sang DELIVERING)", example = "3")
    private Long shipperId;
}
