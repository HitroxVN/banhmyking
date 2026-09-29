package com.banhmyking.banhmyking.dto.report;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Số đơn và doanh thu giao thành công của từng tài xế trong khoảng thời gian đã chọn. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ShipperRevenueResponse {

    private Long shipperId;
    private String shipperName;
    private Long orderCount;
    private BigDecimal revenue;
}
