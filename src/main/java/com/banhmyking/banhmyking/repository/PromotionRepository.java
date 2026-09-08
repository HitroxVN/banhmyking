package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    Optional<Promotion> findByCode(String code);
    Optional<Promotion> findByCodeAndActiveTrue(String code);
}
