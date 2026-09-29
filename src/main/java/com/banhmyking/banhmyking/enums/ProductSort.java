package com.banhmyking.banhmyking.enums;

import org.springframework.data.domain.Sort;

/**
 * Các cách sắp xếp thực đơn mà client được phép chọn.
 *
 * <p>Whitelist có chủ đích: nếu nhận thẳng tên cột từ query param rồi đưa vào {@link Sort},
 * client sắp xếp được theo cột bất kỳ và gõ sai một chữ là nổ 500.
 */
public enum ProductSort {

    /** Mặc định — giữ đúng thứ tự cũ: nổi bật lên đầu, cùng hạng thì theo tên. */
    FEATURED(Sort.by(Sort.Order.desc("featured"), Sort.Order.asc("name"))),
    PRICE_ASC(Sort.by(Sort.Order.asc("price"), Sort.Order.asc("name"))),
    PRICE_DESC(Sort.by(Sort.Order.desc("price"), Sort.Order.asc("name"))),
    NAME(Sort.by(Sort.Order.asc("name"))),
    NEWEST(Sort.by(Sort.Order.desc("createdAt")));

    private final Sort sort;

    ProductSort(Sort sort) {
        this.sort = sort;
    }

    /**
     * Các sort theo giá đều có tên làm khoá phụ: nếu không, hai món cùng giá có thể đổi chỗ
     * giữa hai lần lật trang và khách thấy trùng/thiếu món.
     */
    public Sort toSort() {
        return sort;
    }
}
