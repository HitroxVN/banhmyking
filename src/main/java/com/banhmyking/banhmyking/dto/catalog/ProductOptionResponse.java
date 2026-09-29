package com.banhmyking.banhmyking.dto.catalog;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductOptionResponse {
    private Long id;
    private String name;
    private BigDecimal extraPrice;
    /** NULL = option phẳng không thuộc nhóm nào (dữ liệu cũ). */
    private Long groupId;
}
