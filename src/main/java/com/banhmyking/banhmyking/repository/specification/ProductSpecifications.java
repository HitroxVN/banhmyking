package com.banhmyking.banhmyking.repository.specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.enums.ProductType;

import jakarta.persistence.criteria.Predicate;

public class ProductSpecifications {

    private ProductSpecifications() {}

    /**
     * Lọc thực đơn theo danh mục / trạng thái bán / nổi bật / từ khoá.
     *
     * <p>Từ khoá tìm trên cả tên lẫn mô tả, không phân biệt hoa-thường và <b>không phân biệt
     * dấu</b> — phần bỏ dấu là do collation utf8mb4_unicode_ci của cột làm sẵn, nên "banh mi"
     * vẫn ra "Bánh mì" mà không cần hàm nào lên cột.
     *
     * <p>Còn đúng một lỗ: collation coi {@code đ} là chữ cái riêng, không bằng {@code d}, nên
     * "dac biet" sẽ trượt "đặc biệt". Bù bằng nhánh LIKE thứ hai với {@code d} trong từ khoá đổi
     * thành {@code đ} (đổi ở từ khoá chứ không bọc hàm lên cột, để index còn dùng được).
     */
    public static Specification<Product> search(Long categoryId, boolean availableOnly,
                                                String keyword, Boolean featured,
                                                BigDecimal minPrice, BigDecimal maxPrice) {
        return search(categoryId, availableOnly, keyword, featured, minPrice, maxPrice, null, null, null);
    }

    /**
     * Như trên, thêm lọc loại sản phẩm và "đang khuyến mãi" (spec combo-sale §6.1): món lẻ có giá KM và
     * {@code now} nằm trong [saleStartsAt, saleEndsAt) — cùng điều kiện với ProductPricing.isSaleActive.
     */
    public static Specification<Product> search(Long categoryId, boolean availableOnly,
                                                String keyword, Boolean featured,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                Boolean onSale, ProductType type, LocalDateTime now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (availableOnly) {
                predicates.add(cb.isTrue(root.get("available")));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (featured != null) {
                predicates.add(cb.equal(root.get("featured"), featured));
            }
            // Giá âm/0 coi như không lọc: không có món nào giá 0 nên gửi xuống chỉ tổ rỗng kết quả
            if (minPrice != null && minPrice.signum() > 0) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null && maxPrice.signum() > 0) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (type != null) {
                predicates.add(cb.equal(root.get("productType"), type));
            }
            if (Boolean.TRUE.equals(onSale) && now != null) {
                predicates.add(cb.equal(root.get("productType"), ProductType.SINGLE));
                predicates.add(cb.isNotNull(root.get("salePrice")));
                predicates.add(cb.or(cb.isNull(root.get("saleStartsAt")),
                        cb.lessThanOrEqualTo(root.<LocalDateTime>get("saleStartsAt"), now)));
                predicates.add(cb.or(cb.isNull(root.get("saleEndsAt")),
                        cb.greaterThan(root.<LocalDateTime>get("saleEndsAt"), now)));
            }

            if (keyword != null && !keyword.isBlank()) {
                String trimmed = keyword.trim();
                String plain = "%" + trimmed + "%";
                predicates.add(cb.or(
                        cb.like(root.get("name"), plain),
                        cb.like(root.get("description"), plain)));

                String stroked = "%" + trimmed.replace('d', 'đ').replace('D', 'Đ') + "%";
                if (!stroked.equals(plain)) {
                    predicates.add(cb.or(
                            cb.like(root.get("name"), stroked),
                            cb.like(root.get("description"), stroked)));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
