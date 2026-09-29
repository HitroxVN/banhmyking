package com.banhmyking.banhmyking.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.banhmyking.banhmyking.dto.promotion.PublicPromotionResponse;
import com.banhmyking.banhmyking.dto.promotion.WalletPromotionResponse;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.PromotionService;

/**
 * Standalone MockMvc nên KHÔNG kiểm tra được phân quyền của Spring Security. Hai endpoint tra cứu
 * ({@code /public}, {@code /validate}) vẫn permitAll trong SecurityConfig; {@code /wallet} thì
 * SecurityConfig yêu cầu đăng nhập — ở đây chỉ kiểm tra được hành vi thiếu principal (401).
 */
@ExtendWith(MockitoExtension.class)
class PromotionControllerTest {

    @Mock
    PromotionService promotionService;

    @InjectMocks
    PromotionController promotionController;

    MockMvc mockMvc;

    /** Principal giả — username là userId (JWT subject), user 1 (customer). */
    private static final org.springframework.security.core.userdetails.UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("1")
                    .password("x")
                    .authorities("ROLE_CUSTOMER")
                    .build();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(promotionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPublicPromotions_returnsUsableCodes() throws Exception {
        when(promotionService.getPublicPromotions()).thenReturn(List.of(
                new PublicPromotionResponse("BANHMYKING10", "Giảm 10%", DiscountType.PERCENTAGE,
                        BigDecimal.valueOf(10), BigDecimal.valueOf(30000),
                        BigDecimal.valueOf(50000), LocalDateTime.now().plusDays(5))));

        mockMvc.perform(get("/api/v1/promotions/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].code").value("BANHMYKING10"))
                .andExpect(jsonPath("$.data[0].discountType").value("PERCENTAGE"))
                .andExpect(jsonPath("$.data[0].minOrderAmount").value(50000));
    }

    @Test
    void getPublicPromotions_whenNoUsableCode_returnsEmptyList() throws Exception {
        when(promotionService.getPublicPromotions()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/promotions/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getWallet_returnsAvailableThenUsedCodes() throws Exception {
        when(promotionService.getWallet(1L)).thenReturn(List.of(
                new WalletPromotionResponse("CON_DUNG", "Giảm 10%", DiscountType.PERCENTAGE,
                        BigDecimal.valueOf(10), BigDecimal.valueOf(30000), BigDecimal.ZERO,
                        LocalDateTime.now().plusDays(3), false, null, null, null),
                new WalletPromotionResponse("DA_DUNG", "Giảm 20k", DiscountType.FIXED_AMOUNT,
                        BigDecimal.valueOf(20000), null, BigDecimal.ZERO,
                        LocalDateTime.now().plusDays(3), true, LocalDateTime.now().minusDays(1),
                        "BMK-20260928-ABCDE", BigDecimal.valueOf(20000))));

        mockMvc.perform(get("/api/v1/promotions/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].code").value("CON_DUNG"))
                .andExpect(jsonPath("$.data[0].used").value(false))
                .andExpect(jsonPath("$.data[1].used").value(true))
                .andExpect(jsonPath("$.data[1].orderCode").value("BMK-20260928-ABCDE"));
    }

    @Test
    void getWallet_whenNoPrincipal_returnsUnauthorized() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/promotions/wallet"))
                .andExpect(status().isUnauthorized());
    }
}
