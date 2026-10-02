package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Snapshot thành phần của một dòng combo trong đơn — tên + số lượng tại lúc đặt (spec §2.4). */
@Getter
@Setter
@Entity
@Table(name = "order_item_components")
public class OrderItemComponent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /** NULL nếu món sau này bị xoá cứng — tên vẫn đọc từ snapshot. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    /** Số lượng trong MỘT combo. */
    @Column(nullable = false)
    private Integer quantity;
}
