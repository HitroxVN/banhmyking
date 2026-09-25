package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.cart.AddToCartRequest;
import com.banhmyking.banhmyking.dto.cart.CartResponse;
import com.banhmyking.banhmyking.dto.cart.UpdateCartItemRequest;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.CartService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CartControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController cartController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Principal giả — username là userId (JWT subject), user 1 (customer). */
    private static final org.springframework.security.core.userdetails.UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("1")
                    .password("x")
                    .authorities("ROLE_CUSTOMER")
                    .build();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cartController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/cart - Trả về giỏ hàng thành công")
    void getCart_shouldReturnCart() throws Exception {
        CartResponse response = CartResponse.empty();
        when(cartService.getCart(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalQuantity").value(0))
                .andExpect(jsonPath("$.data.subtotal").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items - Thêm món thành công (201 Created)")
    void addToCart_shouldReturnCreated() throws Exception {
        CartResponse response = CartResponse.builder()
                .cartId(100L)
                .totalQuantity(2)
                .subtotal(BigDecimal.valueOf(60000))
                .build();

        when(cartService.addToCart(eq(1L), any(AddToCartRequest.class))).thenReturn(response);

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(10L)
                .quantity(2)
                .optionIds(List.of(1L, 2L))
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.cartId").value(100))
                .andExpect(jsonPath("$.data.totalQuantity").value(2));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items - Chặn khi món ăn không khả dụng (400 Bad Request)")
    void addToCart_whenUnavailable_shouldReturnBusinessError() throws Exception {
        when(cartService.addToCart(eq(1L), any(AddToCartRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.BUSINESS_ERROR, "Món ăn hiện không khả dụng"));

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(20L)
                .quantity(1)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("Món ăn hiện không khả dụng"));
    }

    @Test
    @DisplayName("POST /api/v1/cart/items - Báo lỗi validation nếu thiếu productId hoặc số lượng < 1")
    void addToCart_validationError() throws Exception {
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(null) // Thiếu
                .quantity(0)    // < 1
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PUT /api/v1/cart/items/{itemId} - Sửa số lượng thành công")
    void updateItemQuantity_shouldReturnUpdatedCart() throws Exception {
        CartResponse response = CartResponse.builder()
                .cartId(100L)
                .totalQuantity(5)
                .subtotal(BigDecimal.valueOf(150000))
                .build();

        when(cartService.updateItemQuantity(eq(1L), eq(500L), any(UpdateCartItemRequest.class)))
                .thenReturn(response);

        UpdateCartItemRequest request = UpdateCartItemRequest.builder()
                .quantity(5)
                .build();

        mockMvc.perform(put("/api/v1/cart/items/500")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalQuantity").value(5));
    }

    @Test
    @DisplayName("DELETE /api/v1/cart/items/{itemId} - Xóa món thành công")
    void removeItem_shouldReturnUpdatedCart() throws Exception {
        CartResponse response = CartResponse.empty();
        when(cartService.removeItem(1L, 500L)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/cart/items/500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /api/v1/cart - Xóa sạch giỏ hàng thành công")
    void clearCart_shouldReturnEmptyCart() throws Exception {
        CartResponse response = CartResponse.empty();
        when(cartService.clearCart(1L)).thenReturn(response);

        mockMvc.perform(delete("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }
}
