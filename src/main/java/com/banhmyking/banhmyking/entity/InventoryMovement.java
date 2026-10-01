package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.InventoryReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Sổ kho — mọi thay đổi tồn đều để lại một dòng. */
@Getter
@Setter
@Entity
@Table(name = "inventory_movements",
        indexes = {
                @Index(name = "idx_inv_mov_product_created", columnList = "product_id, created_at"),
                @Index(name = "idx_inv_mov_order_reason", columnList = "order_id, reason")
        })
public class InventoryMovement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Cơ sở có biến động tồn. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    /** + nhập, - xuất. */
    @Column(name = "change_qty", nullable = false)
    private Integer changeQty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryReason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(length = 300)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
