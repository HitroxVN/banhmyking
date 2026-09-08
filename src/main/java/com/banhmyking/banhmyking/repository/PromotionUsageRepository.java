package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.PromotionUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {
    Optional<PromotionUsage> findByPromotionIdAndUserId(Long promotionId, Long userId);
}
