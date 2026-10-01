package com.banhmyking.banhmyking.dto.store;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StoreResponse {
    private Long id;
    private String code;
    private String name;
    private String address;
    private String phone;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** HH:mm */
    private String openTime;
    private String closeTime;
    private boolean acceptingOrders;
    private BigDecimal deliveryRadiusKm;
    private BigDecimal freeShipRadiusKm;
    private BigDecimal minOrderAmount;
    private boolean active;
    private boolean openNow;
    private long staffCount;
}
