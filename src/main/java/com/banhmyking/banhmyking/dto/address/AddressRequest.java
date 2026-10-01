package com.banhmyking.banhmyking.dto.address;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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

    // ---- Tuỳ chọn: điền từ bản đồ, người dùng sửa tay được ----
    @Size(max = 255)
    private String street;

    @Size(max = 100)
    private String ward;

    @Size(max = 100)
    private String province;

    /** Giới hạn trong khung lãnh thổ Việt Nam — chặn toạ độ rác/đảo lat-lng. */
    @DecimalMin(value = "8.0", message = "Vĩ độ nằm ngoài Việt Nam")
    @DecimalMax(value = "23.5", message = "Vĩ độ nằm ngoài Việt Nam")
    private BigDecimal latitude;

    @DecimalMin(value = "102.0", message = "Kinh độ nằm ngoài Việt Nam")
    @DecimalMax(value = "110.0", message = "Kinh độ nằm ngoài Việt Nam")
    private BigDecimal longitude;

    @Builder.Default
    private boolean defaultAddress = false;
}
