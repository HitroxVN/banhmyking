package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
    Optional<Promotion> findByCodeAndActiveTrue(String code);

    /**
     * Các mã khách thật sự dùng được ngay bây giờ: đang bật, đã tới ngày, chưa hết hạn, còn lượt.
     * Điều kiện lượt phải khớp {@link #incrementUsedCountAtomic} — nếu lệch, danh sách sẽ mời
     * khách chọn một mã mà lúc trừ lượt sẽ nổ.
     * Sắp theo đơn tối thiểu tăng dần: mã dễ dùng nhất lên đầu.
     */
    @Query("SELECT p FROM Promotion p WHERE p.active = true "
            + "AND p.startsAt <= :now AND p.endsAt >= :now "
            + "AND (p.maxUsage <= 0 OR p.usedCount < p.maxUsage) "
            + "ORDER BY p.minOrderAmount ASC")
    List<Promotion> findUsableAt(@Param("now") LocalDateTime now);

    /**
     * Increment usedCount atomically — điều kiện maxUsage nằm trong cùng câu UPDATE nên
     * hai đơn đặt cùng lúc không thể cùng vượt quota. Trả số row cập nhật:
     * 0 = vừa hết lượt, caller phải fail (transaction rollback cả đơn).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Promotion p SET p.usedCount = p.usedCount + 1 "
            + "WHERE p.id = :id AND (p.maxUsage IS NULL OR p.maxUsage <= 0 OR p.usedCount < p.maxUsage)")
    int incrementUsedCountAtomic(@Param("id") Long id);
}
