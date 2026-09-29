package com.banhmyking.banhmyking.dto.report;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Một dòng bảng "món bán chạy". Cũng là kiểu của constructor expression trong
 * {@code OrderItemRepository.findTopProducts} nên constructor phải khớp đúng thứ tự tham số.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopProductResponse {

    private Long productId;
    /** Tên snapshot tại thời điểm bán — món đã xoá/đổi tên vẫn hiện đúng tên đã bán. */
    private String productName;
    private Long quantitySold;
    private BigDecimal revenue;
}
