package com.banhmyking.banhmyking.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin trạng thái khả dụng của tài xế (Shipper)")
public class ShipperAvailabilityResponse {

    @Schema(description = "ID của Shipper", example = "6")
    private Long id;

    @Schema(description = "Họ tên của Shipper", example = "Tài Xế Giao Hàng")
    private String fullName;

    @Schema(description = "Số điện thoại liên hệ", example = "0906665555")
    private String phone;

    @Schema(description = "Địa chỉ email", example = "shipper@banhmyking.vn")
    private String email;

    @Schema(description = "Số đơn hàng shipper đang nhận giao (DELIVERING)", example = "0")
    private long activeOrdersCount;

    @Schema(description = "Trạng thái rảnh (true nếu activeOrdersCount == 0)", example = "true")
    private boolean available;
}
