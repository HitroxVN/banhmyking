package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    boolean existsByOrderCode(String orderCode);

    long countByStoreIdAndStatusIn(Long storeId, List<OrderStatus> statuses);

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByOrderCodeAndUserId(String orderCode, Long userId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /**
     * Đơn online (không COD) còn PENDING và chưa thu được đồng nào quá hạn `cutoff` — dùng cho
     * job tự huỷ đơn treo. Loại COD vì COD trả tiền lúc nhận hàng, không phải lỗi nếu chưa PAID.
     */
    @Query("SELECT o FROM Order o JOIN o.payment p "
           + "WHERE o.status = com.banhmyking.banhmyking.enums.OrderStatus.PENDING "
           + "AND p.status = com.banhmyking.banhmyking.enums.PaymentStatus.PENDING "
           + "AND p.method <> com.banhmyking.banhmyking.enums.PaymentMethod.COD "
           + "AND o.createdAt < :cutoff")
    List<Order> findStalePendingUnpaid(@Param("cutoff") java.time.LocalDateTime cutoff);

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH o.payment " +
           "WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeWithDetails(@Param("orderCode") String orderCode);

    String REVENUE_STATUS = "o.status NOT IN (com.banhmyking.banhmyking.enums.OrderStatus.CANCELLED, "
            + "com.banhmyking.banhmyking.enums.OrderStatus.FAILED)";

    /** storeId / since = null nghĩa là không lọc theo điều kiện đó. */
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE " + REVENUE_STATUS
            + " AND (:storeId IS NULL OR o.store.id = :storeId) AND (:since IS NULL OR o.createdAt >= :since)")
    java.math.BigDecimal sumRevenue(@Param("storeId") Long storeId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) "
            + "AND (:since IS NULL OR o.createdAt >= :since)")
    long countOrders(@Param("storeId") Long storeId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(o) FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) AND o.status IN :statuses")
    long countByStatuses(@Param("storeId") Long storeId, @Param("statuses") List<OrderStatus> statuses);

    long countByShipperIdAndStatus(Long shipperId, OrderStatus status);

    long countByShipperIdAndStatusIn(Long shipperId, List<OrderStatus> statuses);

    long countByShipperIdAndStatusInAndIdNot(Long shipperId, List<OrderStatus> statuses, Long orderId);

    @Query("SELECT o FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) AND o.createdAt >= :from "
            + "ORDER BY o.createdAt ASC")
    List<Order> findForChart(@Param("storeId") Long storeId, @Param("from") java.time.LocalDateTime from);

    /** Đơn trong [from, to) — dùng cho báo cáo doanh thu theo ngày (gộp ở tầng service, xem dashboard). */
    @Query("SELECT o FROM Order o WHERE (:storeId IS NULL OR o.store.id = :storeId) "
            + "AND o.createdAt >= :from AND o.createdAt < :to")
    List<Order> findInRange(@Param("storeId") Long storeId, @Param("from") java.time.LocalDateTime from,
                            @Param("to") java.time.LocalDateTime to);

    /** Doanh thu theo cơ sở trong [from, to). */
    @Query("SELECT new com.banhmyking.banhmyking.dto.report.StoreRevenueResponse("
            + "s.id, s.name, COUNT(o.id), SUM(o.total)) "
            + "FROM Order o JOIN o.store s WHERE " + REVENUE_STATUS
            + " AND o.createdAt >= :from AND o.createdAt < :to GROUP BY s.id, s.name ORDER BY SUM(o.total) DESC")
    List<com.banhmyking.banhmyking.dto.report.StoreRevenueResponse> findRevenueByStore(
            @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to);

    /** Doanh thu theo tài xế trong [from, to). Đơn chưa gán tài xế không thuộc báo cáo này. */
    @Query("SELECT new com.banhmyking.banhmyking.dto.report.ShipperRevenueResponse("
            + "s.id, s.fullName, COUNT(o.id), SUM(o.total)) "
            + "FROM Order o JOIN o.shipper s "
            + "WHERE o.status NOT IN (com.banhmyking.banhmyking.enums.OrderStatus.CANCELLED, "
            + "com.banhmyking.banhmyking.enums.OrderStatus.FAILED) "
            + "AND o.createdAt >= :from AND o.createdAt < :to "
            + "AND (:storeId IS NULL OR o.store.id = :storeId) "
            + "GROUP BY s.id, s.fullName "
            + "ORDER BY SUM(o.total) DESC")
    java.util.List<com.banhmyking.banhmyking.dto.report.ShipperRevenueResponse> findRevenueByShipper(
            @Param("from") java.time.LocalDateTime from, @Param("to") java.time.LocalDateTime to,
            @Param("storeId") Long storeId);
}
