package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.InventoryMovement;
import com.banhmyking.banhmyking.enums.InventoryReason;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    /** Sổ ORDER của một đơn — nguồn sự thật cho việc trừ ở cơ sở nào, bao nhiêu (để hoàn đúng chỗ). */
    List<InventoryMovement> findByOrderIdAndReason(Long orderId, InventoryReason reason);

    boolean existsByOrderIdAndStoreIdAndProductIdAndReason(Long orderId, Long storeId, Long productId,
                                                           InventoryReason reason);

    /** JOIN FETCH order để lấy orderCode khỏi N+1 khi duyệt sổ kho. */
    @Query(value = "SELECT m FROM InventoryMovement m LEFT JOIN FETCH m.order "
                   + "WHERE m.store.id = :storeId AND m.product.id = :productId",
            countQuery = "SELECT COUNT(m) FROM InventoryMovement m "
                   + "WHERE m.store.id = :storeId AND m.product.id = :productId")
    Page<InventoryMovement> findPageByStoreIdAndProductId(@Param("storeId") Long storeId,
                                                         @Param("productId") Long productId,
                                                         Pageable pageable);
}
