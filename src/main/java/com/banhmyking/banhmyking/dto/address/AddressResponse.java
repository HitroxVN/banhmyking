package com.banhmyking.banhmyking.dto.address;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class AddressResponse {
    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String fullAddress;
    private String street;
    private String ward;
    private String province;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private boolean defaultAddress;
}
