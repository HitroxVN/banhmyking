package com.banhmyking.banhmyking.dto.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Getter;

/** Tiền ưu đãi từ giá KM và combo trên đơn đã giao (spec combo-sale §6.5). */
@Getter
public class PriceSavingsResponse {

    /** Σ (giá gốc − giá bán) × số lượng; 0 khi không có dòng nào. */
    private final BigDecimal amount;

    /** Số đơn đã giao có ít nhất một dòng ưu đãi. */
    private final long orderCount;

    /** Constructor cho JPQL `SELECT new` — SUM rỗng trả NULL. */
    public PriceSavingsResponse(BigDecimal amount, Long orderCount) {
        this.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        this.orderCount = orderCount == null ? 0L : orderCount;
    }
}
