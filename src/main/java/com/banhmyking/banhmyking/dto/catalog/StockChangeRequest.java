package com.banhmyking.banhmyking.dto.catalog;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockChangeRequest {

    /** Dương = nhập thêm, âm = giảm bớt. Khác 0. */
    @NotNull
    private Integer changeQty;

    @Size(max = 300)
    private String note;
}
