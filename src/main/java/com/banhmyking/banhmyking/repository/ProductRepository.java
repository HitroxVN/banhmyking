package com.banhmyking.banhmyking.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.banhmyking.banhmyking.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndDeletedFalse(Long id);

    /**
     * Trừ tồn nguyên tử: điều kiện số lượng nằm trong cùng câu UPDATE nên hai đơn xác nhận cùng
     * lúc không thể cùng vượt tồn. Trả số row cập nhật: 0 = không đủ hàng, caller phải fail.
     * Caller bỏ qua sản phẩm không quản tồn trước khi gọi.
     *
     * Không dùng clearAutomatically: nó detach luôn entity Order mà OrderServiceImpl còn dùng
     * sau lời gọi này (items LAZY sẽ nổ khi đọc lại).
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty "
            + "WHERE p.id = :id AND p.stockQuantity IS NOT NULL AND p.stockQuantity >= :qty")
    int decrementStockAtomic(@Param("id") Long id, @Param("qty") int qty);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty "
            + "WHERE p.id = :id AND p.stockQuantity IS NOT NULL")
    int incrementStockAtomic(@Param("id") Long id, @Param("qty") int qty);
}
