package com.banhmyking.banhmyking.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phân vùng khu vực giao hàng")
public enum DeliveryArea {
    INNER_CITY,   // Khu vực nội thành (phí cơ bản)
    SUBURBAN,     // Khu vực ngoại thành (phụ thu ngoại thành)
    OTHER         // Khu vực khác / chưa phân loại
}
