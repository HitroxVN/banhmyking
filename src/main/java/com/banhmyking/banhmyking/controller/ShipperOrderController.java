package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.order.ConfirmDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.FailDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.RejectOrderRequest;
import com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipper/orders")
@RequiredArgsConstructor
@Tag(name = "Shipper Orders", description = "APIs dành cho nhân viên giao hàng (Shipper)")
public class ShipperOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Lấy danh sách đơn hàng được gán cho Shipper (phân trang + lọc)",
            description = "Lấy danh sách các đơn hàng do shipper hiện tại phụ trách giao, hỗ trợ lọc theo trạng thái và phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAssignedOrders(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Lọc theo trạng thái đơn hàng (ví dụ: DELIVERING, DELIVERED, FAILED)")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Số trang (bắt đầu từ 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        Long userId = SecurityUtils.requireUserId(principal);
        PageResponse<OrderResponse> response = orderService.getOrdersForShipper(userId, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đơn hàng giao thành công", response));
    }

    @GetMapping("/{orderCode}")
    @Operation(summary = "Xem chi tiết đơn hàng được gán cho Shipper",
            description = "Lấy chi tiết đơn hàng mà shipper hiện tại được phân công giao.")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByCode(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.getOrderByCode(userId, orderCode);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết đơn hàng thành công", response));
    }

    @PutMapping("/{orderCode}/deliver")
    @Operation(summary = "Xác nhận giao hàng thành công (Shipper)",
            description = "Shipper xác nhận đã giao đơn hàng thành công đến tay khách hàng. Chuyển trạng thái từ DELIVERING sang DELIVERED và cập nhật thanh toán COD nếu có.")
    public ResponseEntity<ApiResponse<OrderResponse>> confirmDelivery(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody(required = false) ConfirmDeliveryRequest request) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.confirmDelivery(userId, orderCode, request);
        return ResponseEntity.ok(ApiResponse.ok("Xác nhận giao hàng thành công", response));
    }

    @PutMapping("/{orderCode}/fail")
    @Operation(summary = "Xác nhận giao hàng thất bại (Shipper)",
            description = "Shipper báo cáo giao hàng không thành công kèm lý do cụ thể. Chuyển trạng thái từ DELIVERING sang FAILED.")
    public ResponseEntity<ApiResponse<OrderResponse>> failDelivery(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody FailDeliveryRequest request) {
        Long userId = SecurityUtils.requireUserId(principal);
        UpdateOrderStatusRequest updateRequest = UpdateOrderStatusRequest.builder()
                .newStatus(OrderStatus.FAILED)
                .note(request.getReason())
                .build();
        OrderResponse response = orderService.updateOrderStatus(userId, orderCode, updateRequest);
        return ResponseEntity.ok(ApiResponse.ok("Báo cáo giao hàng thất bại thành công", response));
    }

    @PutMapping("/{orderCode}/reject")
    @Operation(summary = "Shipper từ chối nhận đơn hàng được gán",
            description = "Shipper từ chối nhận đơn hàng đang ở trạng thái READY_FOR_PICKUP kèm lý do cụ thể. Đơn hàng sẽ được gỡ gán khỏi shipper để nhân viên quán điều phối lại.")
    public ResponseEntity<ApiResponse<OrderResponse>> rejectOrder(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody RejectOrderRequest request) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.rejectAssignedOrder(userId, orderCode, request);
        return ResponseEntity.ok(ApiResponse.ok("Từ chối nhận đơn hàng thành công", response));
    }

    @GetMapping("/{orderCode}/history")
    @Operation(summary = "Lịch sử trạng thái đơn hàng (Shipper)",
            description = "Xem lịch sử trạng thái của đơn hàng được gán.")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getOrderStatusHistory(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode) {
        Long userId = SecurityUtils.requireUserId(principal);
        List<OrderStatusHistoryResponse> history = orderService.getOrderStatusHistory(userId, orderCode);
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử trạng thái đơn hàng thành công", history));
    }
}
