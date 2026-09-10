package com.banhmyking.banhmyking.dto.promotion;

import com.banhmyking.banhmyking.enums.DiscountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PromotionResponse {

    private Long id;
    private String code;
    private String description;
    private DiscountType discountType;
    private BigDecimal value;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Integer maxUsage;
    private Integer usedCount;
    private boolean active;
    private BigDecimal discountApplied;
    private LocalDateTime createdAt;
}
