package com.banhmyking.banhmyking.dto.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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

    /** Tồn ban đầu, chỉ dùng khi tạo mới; sau đó sửa qua endpoint nhập/điều chỉnh kho. NULL = không quản tồn. */
    private Integer stockQuantity;

    /** Ngưỡng cảnh báo sắp hết. Bỏ trống = giữ mặc định 5. */
    @Min(0)
    private Integer lowStockThreshold;

    /**
     * Lựa chọn KHÔNG thuộc nhóm nào. Giữ lại cho payload cũ; form nhân viên chỉ dùng
     * {@link #optionGroups}. Cả hai field được gộp vào một lần đồng bộ duy nhất.
     *
     * <p>Bỏ trống field = giữ nguyên phần lựa chọn phẳng hiện có; gửi mảng (kể cả rỗng) =
     * thay toàn bộ.
     */
    @Valid
    private List<ProductOptionRequest> options;

    /**
     * Nhóm lựa chọn (size/topping), theo đúng thứ tự hiển thị (thành {@code sort_order}).
     *
     * <p>Bỏ trống field = giữ nguyên nhóm hiện có; gửi mảng (kể cả rỗng) = thay toàn bộ, và
     * lựa chọn của nhóm bị bỏ khỏi payload sẽ bị xoá (409 nếu còn trong giỏ khách).
     */
    @Valid
    private List<OptionGroupRequest> optionGroups;

    /**
     * Bộ ảnh chi tiết, theo đúng thứ tự hiển thị. Khác {@code options}: <b>bỏ trống = giữ
     * nguyên bộ ảnh cũ</b> (để các chỗ chỉ sửa 1 field như bật/tắt còn hàng không xoá mất ảnh);
     * muốn xoá sạch thì gửi mảng rỗng.
     */
    @Size(max = 10, message = "Mỗi món tối đa 10 ảnh")
    private List<@Size(max = 500, message = "Đường dẫn ảnh tối đa 500 ký tự") String> images;
}
