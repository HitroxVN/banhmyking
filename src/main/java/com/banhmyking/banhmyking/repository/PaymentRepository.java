package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderId(Long orderId);

    /** Webhook dedup: mã giao dịch của cổng đã ghi nhận cho đơn nào chưa. */
    boolean existsByGatewayTxnId(String gatewayTxnId);
}
