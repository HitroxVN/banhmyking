package com.banhmyking.banhmyking.dto.dashboard;

import com.banhmyking.banhmyking.enums.OrderStatus;
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
@Schema(description = "Thống kê phân bổ đơn hàng theo trạng thái")
public class OrderStatusStatResponse {

    @Schema(description = "Mã trạng thái đơn hàng", example = "DELIVERED")
    private OrderStatus status;

    @Schema(description = "Tên hiển thị tiếng Việt", example = "Giao thành công")
    private String statusLabel;

    @Schema(description = "Số lượng đơn hàng", example = "45")
    private long count;

    @Schema(description = "Tỷ lệ phần trăm (%)", example = "75.5")
    private double percentage;

    @Schema(description = "Tổng giá trị tiền đơn hàng của trạng thái này (VND)", example = "3500000")
    private BigDecimal totalAmount;
}
