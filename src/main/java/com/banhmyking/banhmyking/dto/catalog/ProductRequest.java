package com.banhmyking.banhmyking.dto.catalog;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.banhmyking.banhmyking.enums.ProductType;

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

    /** Chỉ dùng khi TẠO (bỏ trống = SINGLE). Khi sửa: bỏ trống hoặc phải trùng loại hiện có. */
    private ProductType productType;

    /**
     * Giá khuyến mãi — chỉ món lẻ. Bỏ trống = không khuyến mãi (xoá KM đang có, kể cả hai mốc thời gian).
     * Phải > 0 và < price (kiểm ở service để giữ thông báo tiếng Việt).
     */
    private BigDecimal salePrice;

    /** Giờ Việt Nam; bỏ trống = áp dụng ngay. */
    private LocalDateTime saleStartsAt;

    /** Giờ Việt Nam; bỏ trống = không hết hạn. Phải sau saleStartsAt khi có cả hai. */
    private LocalDateTime saleEndsAt;

    /**
     * Thành phần combo. Tạo combo: bắt buộc. Sửa combo: bỏ trống = giữ nguyên thành phần; gửi mảng =
     * thay toàn bộ. Món lẻ: phải bỏ trống hoặc rỗng.
     */
    @Valid
    @Size(max = 20, message = "Combo tối đa 20 món thành phần")
    private List<ComboItemRequest> comboItems;

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
