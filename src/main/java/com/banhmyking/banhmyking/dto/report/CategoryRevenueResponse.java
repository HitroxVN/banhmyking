package com.banhmyking.banhmyking.dto.report;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Doanh thu gộp theo danh mục món trong khoảng thời gian đã chọn. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryRevenueResponse {

    private Long categoryId;
    private String categoryName;
    private Long quantitySold;
    private BigDecimal revenue;
}
