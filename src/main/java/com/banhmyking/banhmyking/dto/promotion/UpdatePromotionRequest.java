package com.banhmyking.banhmyking.dto.promotion;

import com.banhmyking.banhmyking.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePromotionRequest {

    @NotBlank(message = "Mã khuyến mãi không được để trống")
    private String code;

    @NotBlank(message = "Mô tả không được để trống")
    private String description;

    @NotNull(message = "Loại giảm giá không được để trống")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống")
    @DecimalMin(value = "0.01", message = "Giá trị giảm phải lớn hơn 0")
    private BigDecimal value;

    private BigDecimal maxDiscountAmount;

    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startsAt;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    private LocalDateTime endsAt;

    @NotNull(message = "Số lượt sử dụng tối đa không được để trống")
    @Min(value = 1, message = "Số lượt sử dụng tối đa phải từ 1 trở lên")
    private Integer maxUsage;

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    private Boolean active;
}
