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
     * Trừ lượt sử dụng (redemption) bằng câu lệnh UPDATE nguyên tử (atomic) trên DB.
     * Chống race condition khi nhiều user áp mã cùng lúc.
     * Trả về số dòng bị ảnh hưởng (1 nếu thành công, 0 nếu mã đã hết lượt).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Promotion p SET p.usedCount = p.usedCount + 1 WHERE p.id = :id AND (p.maxUsage IS NULL OR p.maxUsage = 0 OR p.usedCount < p.maxUsage)")
    int incrementUsedCountAtomic(@Param("id") Long id);
}
