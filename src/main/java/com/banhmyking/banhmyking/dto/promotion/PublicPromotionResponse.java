package com.banhmyking.banhmyking.dto.promotion;

import com.banhmyking.banhmyking.enums.DiscountType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mã giảm giá hiển thị cho khách ở trang thanh toán.
 *
 * <p>Cố tình KHÔNG trả các field vận hành của {@code PromotionResponse}:
 * <ul>
 *   <li>{@code id} — khách chỉ cần {@code code} để gửi lại lúc validate/đặt đơn.</li>
 *   <li>{@code usedCount} / {@code maxUsage} — số lượt còn lại là thông tin nội bộ; lộ ra
 *       chỉ giúp đối thủ biết chương trình nào đang chạy tốt.</li>
 *   <li>{@code active} / {@code startsAt} — endpoint đã lọc sẵn, trả thêm là thừa.</li>
 * </ul>
 */
public record PublicPromotionResponse(
        String code,
        String description,
        DiscountType discountType,
        BigDecimal value,
        BigDecimal maxDiscountAmount,
        BigDecimal minOrderAmount,
        LocalDateTime endsAt) {
}
