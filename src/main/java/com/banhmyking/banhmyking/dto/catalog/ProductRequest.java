package com.banhmyking.banhmyking.dto.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductRequest {
    @NotNull
    private Long categoryId;

    @NotBlank
    @Size(max = 200)
    private String name;

    private String description;

    @Size(max = 500)
    private String imageUrl;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal price;

    private boolean available = true;
    private boolean featured = false;

    @Valid
    private List<ProductOptionRequest> options = new ArrayList<>();
}
