package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.order.CancelOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.RefundOrderRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@Tag(name = "Admin / Staff Orders", description = "APIs quản lý và vận hành đơn hàng dành cho Nhân viên và Quản trị viên")
public class AdminOrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "Lấy danh sách đơn hàng (phân trang + lọc, theo phạm vi cơ sở)",
            description = "ADMIN thấy toàn chuỗi (lọc được theo ?storeId); STAFF/MANAGER chỉ thấy đơn của cơ sở mình. Hỗ trợ lọc theo trạng thái (?status), khoảng thời gian (?fromDate, ?toDate định dạng yyyy-MM-dd hoặc ISO-8601).")
    public ResponseEntity<ApiResponse<PageResponse<OrderResponse>>> getAllOrders(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Lọc theo trạng thái đơn hàng (ví dụ: PENDING, CONFIRMED, DELIVERING)")
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Từ ngày (hỗ trợ yyyy-MM-dd hoặc yyyy-MM-ddTHH:mm:ss)", example = "2026-09-01")
            @RequestParam(required = false) String fromDate,
            @Parameter(description = "Đến ngày (hỗ trợ yyyy-MM-dd hoặc yyyy-MM-ddTHH:mm:ss)", example = "2026-09-30")
            @RequestParam(required = false) String toDate,
            @Parameter(description = "Số trang (bắt đầu từ 0)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số lượng bản ghi mỗi trang", example = "10")
            @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Lọc theo cơ sở (ADMIN; STAFF/MANAGER luôn bị khoá về cơ sở của mình)")
            @RequestParam(required = false) Long storeId) {
        Long userId = SecurityUtils.requireUserId(principal);
        PageResponse<OrderResponse> response = orderService.getAllOrdersForAdmin(userId, status, fromDate, toDate, storeId, page, size);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách đơn hàng thành công", response));
    }

    @GetMapping("/{orderCode}")
    @Operation(summary = "Xem chi tiết đơn hàng (Staff/Manager cùng cơ sở, Admin)", description = "Lấy chi tiết đơn hàng theo mã. STAFF/MANAGER chỉ xem được đơn thuộc cơ sở của mình (đơn cơ sở khác trả 404).")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByCode(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.getOrderByCode(userId, orderCode);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết đơn hàng thành công", response));
    }

    @PutMapping("/{orderCode}/status")
    @Operation(summary = "Cập nhật trạng thái đơn hàng (Staff/Manager cùng cơ sở, Admin)",
            description = "Chuyển trạng thái đơn hàng theo State Machine. STAFF/MANAGER chỉ thao tác trên đơn của cơ sở mình. Chặn nhảy cóc trạng thái.")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.updateOrderStatus(userId, orderCode, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái đơn hàng thành công", response));
    }

    @PutMapping("/{orderCode}/assign-shipper")
    @Operation(summary = "Gán Shipper cho đơn hàng (Staff/Manager cùng cơ sở, Admin)",
            description = "Chỉ định shipper chịu trách nhiệm giao đơn hàng. Shipper phải thuộc cùng cơ sở với đơn; kiểm tra shipper hợp lệ và trạng thái đơn hàng.")
    public ResponseEntity<ApiResponse<OrderResponse>> assignShipper(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody AssignShipperRequest request) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.assignShipper(userId, orderCode, request);
        return ResponseEntity.ok(ApiResponse.ok("Gán shipper cho đơn hàng thành công", response));
    }

    @PutMapping("/{orderCode}/cancel")
    @Operation(summary = "Hủy đơn hàng (Staff/Manager cùng cơ sở, Admin)",
            description = "Cho phép nhân viên/quản lý của cơ sở sở hữu đơn hoặc quản trị viên hủy đơn hàng tới bước READY_FOR_PICKUP, bắt buộc kèm lý do hủy.")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody CancelOrderRequest request) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.cancelOrder(userId, orderCode, request);
        return ResponseEntity.ok(ApiResponse.ok("Hủy đơn hàng thành công", response));
    }

    @PostMapping("/{orderCode}/refund")
    @Operation(summary = "Hoàn tiền cho đơn đã thu (Staff/Manager cùng cơ sở, Admin)",
            description = "Chuyển thanh toán sang REFUNDED kèm vết số tiền/lý do/người hoàn. Chỉ áp dụng cho đơn đã PAID.")
    public ResponseEntity<ApiResponse<OrderResponse>> refundOrder(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody RefundOrderRequest request) {
        Long userId = SecurityUtils.requireUserId(principal);
        OrderResponse response = orderService.refundOrder(userId, orderCode, request);
        return ResponseEntity.ok(ApiResponse.ok("Hoàn tiền cho đơn hàng thành công", response));
    }

    @GetMapping("/{orderCode}/history")
    @Operation(summary = "Lịch sử trạng thái đơn hàng (Staff/Manager cùng cơ sở, Admin)",
            description = "Lấy danh sách các lần chuyển trạng thái của đơn hàng.")
    public ResponseEntity<ApiResponse<List<OrderStatusHistoryResponse>>> getOrderStatusHistory(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode) {
        Long userId = SecurityUtils.requireUserId(principal);
        List<OrderStatusHistoryResponse> history = orderService.getOrderStatusHistory(userId, orderCode);
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử trạng thái đơn hàng thành công", history));
    }

    @GetMapping("/shippers/available")
    @Operation(summary = "Danh sách Shipper khả dụng kèm số đơn đang giao (Staff/Manager cùng cơ sở, Admin)",
            description = "Lấy danh sách tài xế đang hoạt động (STAFF/MANAGER: chỉ tài xế cơ sở mình; ADMIN: lọc theo ?storeId), thống kê số đơn DELIVERING của từng người để ưu tiên chọn người đang rảnh.")
    public ResponseEntity<ApiResponse<List<com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse>>> getAvailableShippers(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) Long storeId) {
        Long userId = SecurityUtils.requireUserId(principal);
        List<com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse> shippers = orderService.getAvailableShippers(userId, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách shipper khả dụng thành công", shippers));
    }

    @PutMapping("/{orderCode}/store")
    @Operation(summary = "Chuyển đơn PENDING sang cơ sở khác (MANAGER cơ sở hiện tại / ADMIN)")
    public ResponseEntity<ApiResponse<OrderResponse>> transferStore(
            @PathVariable String orderCode,
            @Valid @RequestBody com.banhmyking.banhmyking.dto.store.TransferStoreRequest request,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Chuyển cơ sở thành công",
                orderService.transferStore(SecurityUtils.requireUserId(principal), orderCode, request)));
    }
}
