package com.banhmyking.banhmyking.dto.promotion;

import com.banhmyking.banhmyking.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng trong ví mã của khách: {@code used = false} là mã khả dụng, {@code true} là mã đã dùng. */
public record WalletPromotionResponse(
        String code,
        String description,
        DiscountType discountType,
        BigDecimal value,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        LocalDateTime endsAt,
        boolean used,
        LocalDateTime usedAt,
        String orderCode,
        BigDecimal discountApplied) {
}
