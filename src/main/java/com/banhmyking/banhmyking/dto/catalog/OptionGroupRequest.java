package com.banhmyking.banhmyking.dto.catalog;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Nhóm lựa chọn ("Size", "Topping") gửi kèm món — gửi cả mảng = thay toàn bộ nhóm của món. */
@Getter
@Setter
public class OptionGroupRequest {
    @NotBlank
    @Size(max = 100)
    private String name;

    /** Bắt buộc chọn ít nhất 1 lựa chọn trong nhóm. */
    private boolean required;

    /** Số lựa chọn tối đa; 0 = không giới hạn, 1 = chọn đúng một. */
    @Min(0)
    private int maxChoices;

    @Valid
    private List<ProductOptionRequest> options = new ArrayList<>();
}
