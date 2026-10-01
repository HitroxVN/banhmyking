package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreQuoteOption {
    private Long storeId;
    private String storeCode;
    private String storeName;
    private String storeAddress;
    private String storePhone;
    private String openTime;
    private String closeTime;
    private BigDecimal minOrderAmount;
    /** NULL = không tính được khoảng cách (thiếu toạ độ) — phí theo khu vực */
    private BigDecimal distanceKm;
    private BigDecimal shippingFee;
    private BigDecimal originalFee;
    private boolean freeship;
    private String feeDescription;
    private boolean eligible;
    /** Mã lý do: CLOSED, NOT_ACCEPTING, OUT_OF_RADIUS, BELOW_MIN_ORDER, ITEM_UNAVAILABLE */
    private List<String> reasons;
    /** Câu giải thích tiếng Việt theo cùng thứ tự với reasons */
    private List<String> reasonMessages;
    private List<String> unavailableItems;
}
