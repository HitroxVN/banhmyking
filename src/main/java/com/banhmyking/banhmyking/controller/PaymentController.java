package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "APIs tra cứu và quản lý thanh toán đơn hàng (COD / Chuyển khoản)")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/orders/{orderCode}")
    @Operation(summary = "Xem thông tin thanh toán theo mã đơn hàng",
            description = "Tra cứu chi tiết trạng thái thanh toán (PENDING, PAID...) và số tiền của đơn hàng.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrderCode(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode) {

        Long userId = SecurityUtils.requireUserId(principal);
        PaymentResponse response = paymentService.getPaymentByOrderCode(userId, orderCode);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin thanh toán thành công", response));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "Xem chi tiết thanh toán theo Payment ID",
            description = "Tra cứu chi tiết bản ghi thanh toán theo ID.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentById(
            @org.springframework.security.core.annotation.AuthenticationPrincipal org.springframework.security.core.userdetails.UserDetails principal,
            @Parameter(description = "ID bản ghi thanh toán", example = "1")
            @PathVariable Long paymentId) {

        Long userId = SecurityUtils.requireUserId(principal);
        PaymentResponse response = paymentService.getPaymentById(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin thanh toán thành công", response));
    }
}
