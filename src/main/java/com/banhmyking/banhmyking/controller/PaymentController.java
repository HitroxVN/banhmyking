package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest;
import com.banhmyking.banhmyking.dto.payment.SepayWebhookRequest;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
            @AuthenticationPrincipal UserDetails principal,
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
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "ID bản ghi thanh toán", example = "1")
            @PathVariable Long paymentId) {

        Long userId = SecurityUtils.requireUserId(principal);
        PaymentResponse response = paymentService.getPaymentById(userId, paymentId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin thanh toán thành công", response));
    }

    @PostMapping("/orders/{orderCode}/process")
    @Operation(summary = "Xử lý thanh toán cho đơn hàng",
            description = "Xử lý thanh toán đơn hàng (COD, BANK_TRANSFER, E_WALLET), cập nhật trạng thái đơn sang CONFIRMED và thanh toán sang PAID.")
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @AuthenticationPrincipal UserDetails principal,
            @Parameter(description = "Mã đơn hàng", example = "BMK-20260908-A1B2C")
            @PathVariable String orderCode,
            @Valid @RequestBody(required = false) ProcessPaymentRequest request) {

        Long userId = SecurityUtils.requireUserId(principal);
        PaymentResponse response = paymentService.processPayment(userId, orderCode, request);
        return ResponseEntity.ok(ApiResponse.ok("Xử lý thanh toán thành công", response));
    }

    @PostMapping("/webhook/sepay")
    @Operation(summary = "Webhook tự động từ SePay khi tài khoản nhận tiền",
            description = "Tiếp nhận thông báo biến động số dư ngân hàng (Techcombank) từ SePay, tự động xác nhận đơn hàng sang CONFIRMED và PAID.")
    public ResponseEntity<Map<String, Object>> handleSepayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SepayWebhookRequest request) {

        PaymentResponse response = paymentService.processSepayWebhook(authHeader, request);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Xác nhận thanh toán SePay thành công",
                "data", response != null ? response : Map.of()
        ));
    }
}
