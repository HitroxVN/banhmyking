package com.banhmyking.banhmyking.dto.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Một dòng thành phần khi tạo/sửa combo. */
@Getter
@Setter
public class ComboItemRequest {

    @NotNull(message = "Thiếu món thành phần")
    private Long productId;

    @NotNull(message = "Thiếu số lượng món trong combo")
    @Min(value = 1, message = "Số lượng mỗi món trong combo từ 1 đến 20")
    @Max(value = 20, message = "Số lượng mỗi món trong combo từ 1 đến 20")
    private Integer quantity;
}
