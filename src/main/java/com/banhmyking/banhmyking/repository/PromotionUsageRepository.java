package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.PromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {
    Optional<PromotionUsage> findByPromotionIdAndUserId(Long promotionId, Long userId);
    boolean existsByPromotionId(Long promotionId);

    Optional<PromotionUsage> findByOrderId(Long orderId);

    /** Các promotion user này đã dùng — mỗi mã chỉ dùng được 1 lần (xem validateForOrder). */
    @Query("SELECT u.promotion.id FROM PromotionUsage u WHERE u.user.id = :userId")
    List<Long> findPromotionIdsByUserId(@Param("userId") Long userId);
}
