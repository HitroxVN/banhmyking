package com.banhmyking.banhmyking.dto.order;

/** Snapshot một thành phần của dòng combo: tên lúc đặt + số lượng trong MỘT combo. */
public record OrderItemComponentResponse(String productName, Integer quantity) {
}
