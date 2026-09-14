package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Order;
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

    Optional<Order> findByOrderCode(String orderCode);

    Optional<Order> findByOrderCodeAndUserId(String orderCode, Long userId);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o " +
           "LEFT JOIN FETCH o.items i " +
           "LEFT JOIN FETCH o.payment " +
           "WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeWithDetails(@Param("orderCode") String orderCode);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.status NOT IN (com.banhmyking.banhmyking.enums.OrderStatus.CANCELLED, com.banhmyking.banhmyking.enums.OrderStatus.FAILED)")
    java.math.BigDecimal sumTotalRevenue();

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.createdAt >= :startDate AND o.status NOT IN (com.banhmyking.banhmyking.enums.OrderStatus.CANCELLED, com.banhmyking.banhmyking.enums.OrderStatus.FAILED)")
    java.math.BigDecimal sumRevenueSince(@Param("startDate") java.time.LocalDateTime startDate);

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime startDate);

    long countByStatus(OrderStatus status);

    long countByStatusIn(List<OrderStatus> statuses);

    long countByShipperIdAndStatus(Long shipperId, OrderStatus status);

    long countByShipperIdAndStatusIn(Long shipperId, List<OrderStatus> statuses);

    long countByShipperIdAndStatusInAndIdNot(Long shipperId, List<OrderStatus> statuses, Long orderId);

    List<Order> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(java.time.LocalDateTime startDate);
}
