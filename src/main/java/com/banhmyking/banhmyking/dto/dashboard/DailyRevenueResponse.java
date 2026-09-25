package com.banhmyking.banhmyking.dto.dashboard;

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
@Schema(description = "Thống kê doanh thu và đơn hàng theo từng ngày")
public class DailyRevenueResponse {

    @Schema(description = "Ngày thống kê (định dạng yyyy-MM-dd)", example = "2026-09-14")
    private String date;

    @Schema(description = "Tổng doanh thu trong ngày (VND)", example = "850000")
    private BigDecimal revenue;

    @Schema(description = "Số lượng đơn hàng trong ngày", example = "12")
    private long orderCount;
}
