package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Nhóm lựa chọn của một món — "Size" (bắt buộc chọn 1) hay "Topping" (chọn nhiều).
 *
 * <p>Món cũ có thể còn option {@code group_id NULL} (danh sách phẳng trước đây): chúng vẫn
 * bán được và không chịu luật bắt buộc/giới hạn nào — xem {@code CartServiceImpl}.
 */
@Getter
@Setter
@Entity
@Table(name = "option_groups")
public class OptionGroup extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 100)
    private String name;

    /** Bắt buộc chọn ít nhất 1 lựa chọn trong nhóm. */
    @Column(name = "is_required", nullable = false)
    private boolean required;

    /** Số lựa chọn tối đa; 0 = không giới hạn, 1 = chọn đúng một (radio). */
    @Column(name = "max_choices", nullable = false)
    private int maxChoices;

    /** Thứ tự hiển thị của nhóm, tăng dần. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    private List<ProductOption> options = new ArrayList<>();
}
