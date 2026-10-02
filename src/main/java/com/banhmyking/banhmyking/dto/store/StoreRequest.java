package com.banhmyking.banhmyking.dto.store;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record StoreRequest(
        @NotBlank @Size(max = 20) @Pattern(regexp = "^\\s*[A-Za-z0-9_-]+\\s*$", message = "Mã chỉ gồm chữ, số, - và _") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String address,
        @Size(max = 20) String phone,
        @DecimalMin(value = "8.0", message = "Vĩ độ nằm ngoài Việt Nam") @DecimalMax(value = "23.5", message = "Vĩ độ nằm ngoài Việt Nam") BigDecimal latitude,
        @DecimalMin(value = "102.0", message = "Kinh độ nằm ngoài Việt Nam") @DecimalMax(value = "110.0", message = "Kinh độ nằm ngoài Việt Nam") BigDecimal longitude,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ dạng HH:mm") String openTime,
        @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ dạng HH:mm") String closeTime,
        @NotNull @DecimalMin(value = "0.5") @DecimalMax(value = "100") BigDecimal deliveryRadiusKm,
        @NotNull @DecimalMin(value = "0") @DecimalMax(value = "100") BigDecimal freeShipRadiusKm,
        @NotNull @DecimalMin(value = "0") BigDecimal minOrderAmount,
        boolean active) {
}
