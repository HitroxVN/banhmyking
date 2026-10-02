package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalTime;
import lombok.Getter;
import lombok.Setter;

/** Một cơ sở trong chuỗi (spec §2.1). */
@Getter
@Setter
@Entity
@Table(name = "stores")
public class Store extends BaseEntity {

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(length = 20)
    private String phone;

    /** NULL = chưa ghim — cơ sở phục vụ mọi địa chỉ, phí theo khu vực (spec §3.4). */
    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime = LocalTime.of(6, 30);

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime = LocalTime.of(22, 0);

    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders = true;

    @Column(name = "delivery_radius_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal deliveryRadiusKm = new BigDecimal("5.00");

    @Column(name = "free_ship_radius_km", nullable = false, precision = 5, scale = 2)
    private BigDecimal freeShipRadiusKm = new BigDecimal("3.00");

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public boolean hasLocation() {
        return latitude != null && longitude != null;
    }
}
