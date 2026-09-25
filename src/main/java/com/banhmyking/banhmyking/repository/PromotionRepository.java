package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
    Optional<Promotion> findByCodeAndActiveTrue(String code);

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
