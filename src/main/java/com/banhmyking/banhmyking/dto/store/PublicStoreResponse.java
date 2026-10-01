package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;

/** Thông tin cơ sở cho trang "Hệ thống cửa hàng" — không lộ cấu hình phí/bán kính. */
public record PublicStoreResponse(Long id, String code, String name, String address, String phone,
                                  BigDecimal latitude, BigDecimal longitude, String openTime, String closeTime,
                                  boolean openNow, boolean acceptingOrders) {
}
