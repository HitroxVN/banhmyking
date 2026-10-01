package com.banhmyking.banhmyking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "addresses")
public class Address extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Column(name = "full_address", nullable = false, length = 500)
    private String fullAddress;

    // ---- Các ô tách (địa giới 2 cấp) + toạ độ ghim trên bản đồ — đều tuỳ chọn (V10) ----
    @Column(length = 255)
    private String street;

    @Column(length = 100)
    private String ward;

    @Column(length = 100)
    private String province;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "is_default", nullable = false)
    private boolean defaultAddress = false;
}
