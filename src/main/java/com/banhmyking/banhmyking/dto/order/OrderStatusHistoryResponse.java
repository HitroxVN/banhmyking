package com.banhmyking.banhmyking.dto.order;

import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thông tin lịch sử chuyển đổi trạng thái đơn hàng")
public class OrderStatusHistoryResponse {

    @Schema(description = "ID bản ghi lịch sử", example = "1")
    private Long id;

    @Schema(description = "Mã đơn hàng", example = "BMK-20260908-ABC12")
    private String orderCode;

    @Schema(description = "Trạng thái trước", example = "PENDING")
    private OrderStatus fromStatus;

    @Schema(description = "Trạng thái sau", example = "CONFIRMED")
    private OrderStatus toStatus;

    @Schema(description = "ID người thực hiện", example = "1")
    private Long changedById;

    @Schema(description = "Tên người thực hiện", example = "Admin User")
    private String changedByName;

    @Schema(description = "Vai trò người thực hiện", example = "ADMIN")
    private RoleName changedByRole;

    @Schema(description = "Ghi chú hoặc lý do thay đổi", example = "Đã xác nhận đơn hàng")
    private String note;

    @Schema(description = "Thời gian thay đổi", example = "2026-09-09T08:30:00")
    private LocalDateTime createdAt;
}
