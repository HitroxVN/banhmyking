package com.banhmyking.banhmyking.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** Dòng đơn — SNAPSHOT tên + giá món tại lúc đặt, không phụ thuộc bảng products sau này. */
@Getter
@Setter
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** FK để báo cáo/thống kê — tên & giá thì đọc snapshot dưới đây. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    // ---- Snapshot ----
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /** Giá gốc 1 đơn vị (chưa gồm topping) lúc đặt; NULL ở đơn cũ = không có ưu đãi. */
    @Column(name = "original_unit_price", precision = 12, scale = 2)
    private BigDecimal originalUnitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemOption> options = new ArrayList<>();

    /** Snapshot thành phần khi dòng là combo; rỗng với món lẻ. */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<OrderItemComponent> components = new ArrayList<>();

    @OneToOne(mappedBy = "orderItem", fetch = FetchType.LAZY)
    private Review review;
}
