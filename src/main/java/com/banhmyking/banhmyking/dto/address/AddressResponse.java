package com.banhmyking.banhmyking.dto.address;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AddressResponse {
    private Long id;
    private Long userId;
    private String receiverName;
    private String receiverPhone;
    private String fullAddress;
    private boolean defaultAddress;
}
