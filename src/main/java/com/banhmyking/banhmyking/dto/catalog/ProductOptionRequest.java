package com.banhmyking.banhmyking.dto.catalog;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductOptionRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal extraPrice;
}
