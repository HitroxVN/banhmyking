package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.dto.report.CategoryRevenueResponse;
import com.banhmyking.banhmyking.dto.report.TopProductResponse;
import com.banhmyking.banhmyking.entity.OrderItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * Món bán chạy trong [from, to). Bỏ đơn đã huỷ / giao thất bại — chỉ tính đơn thực sự bán được.
     * LEFT JOIN để dòng có product_id null (món đã xoá cứng) vẫn gom theo tên snapshot.
     */
    @Query("SELECT new com.banhmyking.banhmyking.dto.report.TopProductResponse("
            + "p.id, oi.productName, SUM(oi.quantity), SUM(oi.lineTotal)) "
            + "FROM OrderItem oi LEFT JOIN oi.product p "
            + "WHERE oi.order.status NOT IN (com.banhmyking.banhmyking.enums.OrderStatus.CANCELLED, "
            + "com.banhmyking.banhmyking.enums.OrderStatus.FAILED) "
            + "AND oi.order.createdAt >= :from AND oi.order.createdAt < :to "
            + "GROUP BY p.id, oi.productName "
            + "ORDER BY SUM(oi.quantity) DESC")
    List<TopProductResponse> findTopProducts(@Param("from") LocalDateTime from,
                                            @Param("to") LocalDateTime to,
                                            Pageable pageable);

    /** Doanh thu theo danh mục trong [from, to). Chỉ gồm dòng còn gắn được với món + danh mục. */
    @Query("SELECT new com.banhmyking.banhmyking.dto.report.CategoryRevenueResponse("
            + "c.id, c.name, SUM(oi.quantity), SUM(oi.lineTotal)) "
            + "FROM OrderItem oi JOIN oi.product p JOIN p.category c "
            + "WHERE oi.order.status NOT IN (com.banhmyking.banhmyking.enums.OrderStatus.CANCELLED, "
            + "com.banhmyking.banhmyking.enums.OrderStatus.FAILED) "
            + "AND oi.order.createdAt >= :from AND oi.order.createdAt < :to "
            + "GROUP BY c.id, c.name "
            + "ORDER BY SUM(oi.lineTotal) DESC")
    List<CategoryRevenueResponse> findRevenueByCategory(@Param("from") LocalDateTime from,
                                                       @Param("to") LocalDateTime to);
}
