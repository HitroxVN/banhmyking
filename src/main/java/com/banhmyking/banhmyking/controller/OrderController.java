package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.order.CreateOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Order", description = "APIs quản lý đơn hàng")
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Tạo đơn hàng từ giỏ", description = "Tạo đơn hàng từ giỏ hàng hiện tại, snapshot giá món & topping, tính toán tiền qua PriceCalculator, sinh mã BMK-yyyyMMdd-XXXXX và tự động xóa sạch giỏ hàng.")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @Valid @RequestBody CreateOrderRequest request) {
        Long userId = resolveUserId(headerUserId);
        OrderResponse orderResponse = orderService.createFromCart(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo đơn hàng thành công", orderResponse));
    }

    @GetMapping("/{orderCode}")
    @Operation(summary = "Xem chi tiết đơn hàng", description = "Lấy thông tin chi tiết đơn hàng theo mã đơn hàng (orderCode).")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByCode(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @Parameter(description = "Mã đơn hàng (ví dụ: BMK-20260908-A1B2C)", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode) {
        Long userId = resolveUserId(headerUserId);
        OrderResponse orderResponse = orderService.getOrderByCode(userId, orderCode);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết đơn hàng thành công", orderResponse));
    }

    @GetMapping
    @Operation(summary = "Lịch sử đơn hàng của tôi", description = "Lấy danh sách các đơn hàng đã đặt của người dùng, sắp xếp mới nhất lên đầu.")
    public ResponseEntity<ApiResponse<List<OrderResponse>>> getUserOrders(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId) {
        Long userId = resolveUserId(headerUserId);
        List<OrderResponse> orders = orderService.getUserOrders(userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử đơn hàng thành công", orders));
    }

    private Long resolveUserId(Long headerUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            // Sẵn sàng tích hợp khi Auth Filter hoàn tất
        }
        return headerUserId != null ? headerUserId : 1L;
    }
}
