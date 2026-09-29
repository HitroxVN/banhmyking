package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.InventoryMovement;
import com.banhmyking.banhmyking.enums.InventoryReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    boolean existsByOrderIdAndProductIdAndReason(Long orderId, Long productId, InventoryReason reason);

    /** JOIN FETCH order để lấy orderCode khỏi N+1 khi duyệt sổ kho. */
    @Query(value = "SELECT m FROM InventoryMovement m LEFT JOIN FETCH m.order WHERE m.product.id = :productId",
            countQuery = "SELECT COUNT(m) FROM InventoryMovement m WHERE m.product.id = :productId")
    Page<InventoryMovement> findPageByProductId(@Param("productId") Long productId, Pageable pageable);
}
