package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.PromotionUsage;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.service.impl.PromotionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionUsageRepository promotionUsageRepository;

    @InjectMocks
    private PromotionServiceImpl promotionService;

    private Promotion testPromotion;
    private User testUser;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        testPromotion = Promotion.builder()
                .code("PROMO100")
                .description("Mã giảm giá test")
                .discountType(DiscountType.FIXED_AMOUNT)
                .value(BigDecimal.valueOf(10000))
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(5))
                .maxUsage(10)
                .usedCount(2)
                .active(true)
                .build();
        testPromotion.setId(100L);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@banhmyking.com");

        testOrder = new Order();
        testOrder.setId(50L);
    }

    @Test
    @DisplayName("AC 2: Tăng lượt sử dụng thành công khi số dòng bị ảnh hưởng = 1")
    void incrementUsedCountAtomic_success() {
        when(promotionRepository.incrementUsedCountAtomic(100L)).thenReturn(1);

        int affectedRows = promotionService.incrementUsedCountAtomic(100L);

        assertThat(affectedRows).isEqualTo(1);
        verify(promotionRepository).incrementUsedCountAtomic(100L);
    }

    @Test
    @DisplayName("AC 3: Bắn lỗi BusinessException hết lượt sử dụng khi số dòng bị ảnh hưởng = 0")
    void incrementUsedCountAtomic_whenAffectedRowsZero_shouldThrowBusinessException() {
        when(promotionRepository.incrementUsedCountAtomic(100L)).thenReturn(0);

        assertThatThrownBy(() -> promotionService.incrementUsedCountAtomic(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mã khuyến mãi đã hết lượt sử dụng");

        verify(promotionRepository).incrementUsedCountAtomic(100L);
    }

    @Test
    @DisplayName("Thực hiện redeemPromotion thành công và lưu PromotionUsage")
    void redeemPromotion_success() {
        when(promotionRepository.incrementUsedCountAtomic(100L)).thenReturn(1);
        when(promotionUsageRepository.save(any(PromotionUsage.class))).thenAnswer(invocation -> {
            PromotionUsage usage = invocation.getArgument(0);
            usage.setId(1000L);
            return usage;
        });

        PromotionUsage usage = promotionService.redeemPromotion(testPromotion, testUser, testOrder, BigDecimal.valueOf(10000));

        assertThat(usage).isNotNull();
        assertThat(usage.getId()).isEqualTo(1000L);
        assertThat(usage.getDiscountApplied()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        verify(promotionRepository).incrementUsedCountAtomic(100L);
        verify(promotionUsageRepository).save(any(PromotionUsage.class));
    }

    @Test
    @DisplayName("redeemPromotion thất bại khi promotion đã hết lượt sử dụng")
    void redeemPromotion_whenExhausted_shouldThrowAndNotSaveUsage() {
        when(promotionRepository.incrementUsedCountAtomic(100L)).thenReturn(0);

        assertThatThrownBy(() -> promotionService.redeemPromotion(testPromotion, testUser, testOrder, BigDecimal.valueOf(10000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Mã khuyến mãi đã hết lượt sử dụng");

        verify(promotionUsageRepository, never()).save(any());
    }
}
