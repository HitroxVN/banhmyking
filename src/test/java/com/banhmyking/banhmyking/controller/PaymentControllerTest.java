package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.payment.PaymentResponse;
import com.banhmyking.banhmyking.dto.payment.ProcessPaymentRequest;
import com.banhmyking.banhmyking.dto.payment.SepayWebhookRequest;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.PaymentStatus;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UserDetails PRINCIPAL = User
            .withUsername("10")
            .password("x")
            .authorities("ROLE_CUSTOMER")
            .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payments/orders/{orderCode} - Lấy payment thành công")
    void getPaymentByOrderCode_success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .orderId(100L)
                .orderCode("BMK-20260912-TEST1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(115000))
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentService.getPaymentByOrderCode(eq(10L), eq("BMK-20260912-TEST1"))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/orders/BMK-20260912-TEST1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.orderCode").value("BMK-20260912-TEST1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId} - Lấy payment theo ID thành công")
    void getPaymentById_success() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .orderId(100L)
                .orderCode("BMK-20260912-TEST1")
                .method(PaymentMethod.COD)
                .status(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(115000))
                .createdAt(LocalDateTime.now())
                .build();

        when(paymentService.getPaymentById(eq(10L), eq(1L))).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.method").value("COD"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/orders/{orderCode}/process - Xử lý thanh toán thành công")
    void processPayment_success() throws Exception {
        ProcessPaymentRequest req = ProcessPaymentRequest.builder()
                .method(PaymentMethod.BANK_TRANSFER)
                .transactionRef("TXN-12345")
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .orderId(100L)
                .orderCode("BMK-20260912-TEST1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PAID)
                .gatewayTxnId("TXN-12345")
                .amount(BigDecimal.valueOf(115000))
                .build();

        when(paymentService.processPayment(eq(10L), eq("BMK-20260912-TEST1"), any(ProcessPaymentRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/orders/BMK-20260912-TEST1/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.gatewayTxnId").value("TXN-12345"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/webhook/sepay - Webhook SePay thành công")
    void handleSepayWebhook_success() throws Exception {
        SepayWebhookRequest req = SepayWebhookRequest.builder()
                .id(92704L)
                .gateway("Techcombank")
                .accountNumber("8888332999")
                .content("Thanh toan don BMK-20260912-TEST1 banh my king")
                .transferType("in")
                .transferAmount(BigDecimal.valueOf(115000))
                .referenceCode("FT26258012345678")
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(1L)
                .orderId(100L)
                .orderCode("BMK-20260912-TEST1")
                .method(PaymentMethod.BANK_TRANSFER)
                .status(PaymentStatus.PAID)
                .amount(BigDecimal.valueOf(115000))
                .gatewayTxnId("FT26258012345678")
                .build();

        when(paymentService.processSepayWebhook(any(), any(SepayWebhookRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/webhook/sepay")
                        .header("Authorization", "Apikey test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Xác nhận thanh toán SePay thành công"))
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }
}
