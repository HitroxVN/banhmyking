package com.banhmyking.banhmyking.dto.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressRequest {

    @NotBlank
    @Size(max = 100)
    private String receiverName;

    @NotBlank
    @Size(max = 20)
    private String receiverPhone;

    @NotBlank
    @Size(max = 500)
    private String fullAddress;

    @Builder.Default
    private boolean defaultAddress = false;
}
