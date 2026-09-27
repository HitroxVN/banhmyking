package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    /** Số tiền thực thu bằng đúng `orders.total`. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Mã giao dịch từ cổng thanh toán (VNPay/MoMo) — null với COD. */
    @Column(name = "gateway_txn_id", length = 100)
    private String gatewayTxnId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ---- Vết hoàn tiền: chỉ có giá trị khi status = REFUNDED ----

    /** Số tiền đã hoàn — luôn bằng `amount` ở luồng hoàn toàn phần. */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_reason", length = 300)
    private String refundReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    /** Người bấm hoàn tiền (STAFF/ADMIN); null = hệ thống tự hoàn. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refunded_by")
    private User refundedBy;
}
