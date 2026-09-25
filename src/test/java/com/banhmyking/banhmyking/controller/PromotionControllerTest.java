package com.banhmyking.banhmyking.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.banhmyking.banhmyking.dto.promotion.PublicPromotionResponse;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.PromotionService;

/**
 * Standalone MockMvc nên KHÔNG kiểm tra được phân quyền. Endpoint này nằm dưới
 * {@code /api/v1/promotions/**} — đã permitAll sẵn trong SecurityConfig, xác nhận thêm bằng curl.
 */
@ExtendWith(MockitoExtension.class)
class PromotionControllerTest {

    @Mock
    PromotionService promotionService;

    @InjectMocks
    PromotionController promotionController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(promotionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
}
