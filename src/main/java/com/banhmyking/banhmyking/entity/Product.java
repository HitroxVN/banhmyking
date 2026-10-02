package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Getter
@Setter
@Entity
@Table(name = "products",
        indexes = {
                @Index(name = "idx_products_category", columnList = "category_id"),
                @Index(name = "idx_products_name", columnList = "name")
        })
@BatchSize(size = 50)
public class Product extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType = ProductType.SINGLE;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 1 ảnh là đủ (xem mục 4.1 PLAN). */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Giá khuyến mãi (chỉ món lẻ); NULL = không KM. Hiệu lực tính ở ProductPricing. */
    @Column(name = "sale_price", precision = 12, scale = 2)
    private BigDecimal salePrice;

    /** Giờ Việt Nam; NULL = áp dụng ngay. */
    @Column(name = "sale_starts_at")
    private LocalDateTime saleStartsAt;

    /** Giờ Việt Nam; NULL = không hết hạn. */
    @Column(name = "sale_ends_at")
    private LocalDateTime saleEndsAt;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "is_featured", nullable = false)
    private boolean featured = false;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<OptionGroup> optionGroups = new ArrayList<>();

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>();

    /** Thành phần (chỉ combo). Ghi qua ComboItemRepository — phía này chỉ đọc. */
    @OneToMany(mappedBy = "combo", fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<ComboItem> comboItems = new ArrayList<>();

    public boolean isCombo() {
        return productType == ProductType.COMBO;
    }
}
