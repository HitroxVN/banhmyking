package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Tình trạng một món tại một cơ sở (spec §2.2). Không có dòng = đang bán, không quản tồn.
 * Khoá ghép nên không kế thừa BaseEntity (BaseEntity có cột id tự tăng).
 */
@Getter
@Setter
@Entity
@Table(name = "store_products")
@EntityListeners(AuditingEntityListener.class)
public class StoreProduct {

    @EmbeddedId
    private StoreProductId id;

    @MapsId("storeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    /** NULL = không quản tồn tại cơ sở này. */
    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = 5;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isLowStock() {
        return stockQuantity != null && stockQuantity <= lowStockThreshold;
    }
}
