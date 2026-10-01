package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.StoreProduct;
import com.banhmyking.banhmyking.entity.StoreProductId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreProductRepository extends JpaRepository<StoreProduct, StoreProductId> {

    List<StoreProduct> findByIdStoreId(Long storeId);

    List<StoreProduct> findByIdStoreIdAndIdProductIdIn(Long storeId, Collection<Long> productIds);

    Optional<StoreProduct> findByIdStoreIdAndIdProductId(Long storeId, Long productId);

    /** Trừ tồn nguyên tử tại một cơ sở. 0 = không đủ hàng / không quản tồn. Không clearAutomatically (giữ Order đang managed). */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE StoreProduct sp SET sp.stockQuantity = sp.stockQuantity - :qty "
            + "WHERE sp.id.storeId = :storeId AND sp.id.productId = :productId "
            + "AND sp.stockQuantity IS NOT NULL AND sp.stockQuantity >= :qty")
    int decrementStockAtomic(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE StoreProduct sp SET sp.stockQuantity = sp.stockQuantity + :qty "
            + "WHERE sp.id.storeId = :storeId AND sp.id.productId = :productId AND sp.stockQuantity IS NOT NULL")
    int incrementStockAtomic(@Param("storeId") Long storeId, @Param("productId") Long productId, @Param("qty") int qty);
}
