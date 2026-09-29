package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.promotion.PublicPromotionResponse;
import com.banhmyking.banhmyking.dto.promotion.WalletPromotionResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.PromotionUsage;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.service.impl.PromotionServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

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

    // --- Tests cho PROMO-02 (Admin CRUD Promotions & Validation) ---

    @Test
    @DisplayName("PROMO-02: Tạo mã giảm giá thành công khi dữ liệu hợp lệ")
    void createPromotion_success() {
        com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest req =
                com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest.builder()
                        .code("NEWYEAR2026")
                        .description("Giảm 15% tối đa 30k")
                        .discountType(DiscountType.PERCENTAGE)
                        .value(BigDecimal.valueOf(15))
                        .maxDiscountAmount(BigDecimal.valueOf(30000))
                        .minOrderAmount(BigDecimal.valueOf(50000))
                        .startsAt(LocalDateTime.now().minusDays(1))
                        .endsAt(LocalDateTime.now().plusDays(10))
                        .maxUsage(50)
                        .active(true)
                        .build();

        when(promotionRepository.findByCode("NEWYEAR2026")).thenReturn(java.util.Optional.empty());
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(inv -> {
            Promotion p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });

        com.banhmyking.banhmyking.dto.promotion.PromotionResponse res = promotionService.createPromotion(req);

        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(200L);
        assertThat(res.getCode()).isEqualTo("NEWYEAR2026");
        assertThat(res.getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
        verify(promotionRepository).save(any(Promotion.class));
    }

    @Test
    @DisplayName("PROMO-02: Bắn lỗi khi ngày bắt đầu >= ngày kết thúc")
    void createPromotion_whenStartsAtAfterEndsAt_shouldThrowValidationException() {
        com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest req =
                com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest.builder()
                        .code("INVALIDDATE")
                        .description("Test ngày lỗi")
                        .discountType(DiscountType.FIXED_AMOUNT)
                        .value(BigDecimal.valueOf(10000))
                        .startsAt(LocalDateTime.now().plusDays(5))
                        .endsAt(LocalDateTime.now().plusDays(1)) // Ends before starts
                        .maxUsage(10)
                        .build();

        when(promotionRepository.findByCode("INVALIDDATE")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> promotionService.createPromotion(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày bắt đầu phải nhỏ hơn ngày kết thúc");
    }

    @Test
    @DisplayName("PROMO-02: Bắn lỗi khi loại PERCENTAGE thiếu maxDiscountAmount")
    void createPromotion_whenPercentageWithoutMaxDiscountAmount_shouldThrowValidationException() {
        com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest req =
                com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest.builder()
                        .code("NOMAXDISCOUNT")
                        .description("Giảm 20% không giới hạn")
                        .discountType(DiscountType.PERCENTAGE)
                        .value(BigDecimal.valueOf(20))
                        .maxDiscountAmount(null) // Thiếu maxDiscountAmount
                        .startsAt(LocalDateTime.now().minusDays(1))
                        .endsAt(LocalDateTime.now().plusDays(5))
                        .maxUsage(10)
                        .build();

        when(promotionRepository.findByCode("NOMAXDISCOUNT")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> promotionService.createPromotion(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PERCENTAGE bắt buộc phải truyền giá trị maxDiscountAmount");
    }

    @Test
    @DisplayName("PROMO-02: Xóa thành công mã chưa được dùng (Hard Delete)")
    void deletePromotion_whenNoUsages_shouldDeletePermanently() {
        when(promotionRepository.findById(100L)).thenReturn(java.util.Optional.of(testPromotion));
        when(promotionUsageRepository.existsByPromotionId(100L)).thenReturn(false);

        promotionService.deletePromotion(100L);

        verify(promotionRepository).delete(testPromotion);
    }

    @Test
    @DisplayName("PROMO-02: Xóa mềm mã đã có lịch sử sử dụng (Soft Deactivate)")
    void deletePromotion_whenHasUsages_shouldSoftDeactivate() {
        when(promotionRepository.findById(100L)).thenReturn(java.util.Optional.of(testPromotion));
        when(promotionUsageRepository.existsByPromotionId(100L)).thenReturn(true);

        promotionService.deletePromotion(100L);

        assertThat(testPromotion.isActive()).isFalse();
        verify(promotionRepository).save(testPromotion);
        verify(promotionRepository, never()).delete(any());
    }

    // --- Tests cho PROMO-03 (Validate & Atomic Redemption Race Condition) ---

    @Test
    @DisplayName("PROMO-03: validateForOrder hợp lệ khi thỏa mãn mọi điều kiện")
    void validateForOrder_success() {
        when(promotionRepository.findByCodeAndActiveTrue("PROMO100")).thenReturn(java.util.Optional.of(testPromotion));
        when(promotionUsageRepository.findByPromotionIdAndUserId(100L, 1L)).thenReturn(java.util.Optional.empty());

        Promotion promo = promotionService.validateForOrder("PROMO100", 1L, BigDecimal.valueOf(50000));

        assertThat(promo).isNotNull();
        assertThat(promo.getCode()).isEqualTo("PROMO100");
    }

    @Test
    @DisplayName("PROMO-03: validateForOrder ném lỗi khi user đã sử dụng mã trước đây")
    void validateForOrder_whenUserAlreadyUsed_shouldThrowBusinessException() {
        when(promotionRepository.findByCodeAndActiveTrue("PROMO100")).thenReturn(java.util.Optional.of(testPromotion));
        when(promotionUsageRepository.findByPromotionIdAndUserId(100L, 1L))
                .thenReturn(java.util.Optional.of(new PromotionUsage()));

        assertThatThrownBy(() -> promotionService.validateForOrder("PROMO100", 1L, BigDecimal.valueOf(50000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn đã sử dụng mã khuyến mãi");
    }

    @Test
    @DisplayName("PROMO-03: Race condition test - 2 thread cùng gọi áp mã cuối cùng, chỉ 1 thread thành công")
    void redeemPromotion_raceCondition_onlyOneThreadSucceeds() throws Exception {
        java.util.concurrent.atomic.AtomicInteger remainingUsages = new java.util.concurrent.atomic.AtomicInteger(1);

        when(promotionRepository.incrementUsedCountAtomic(100L)).thenAnswer(inv -> {
            if (remainingUsages.getAndDecrement() > 0) {
                return 1;
            } else {
                return 0;
            }
        });

        when(promotionUsageRepository.save(any(PromotionUsage.class))).thenAnswer(inv -> inv.getArgument(0));

        int numberOfThreads = 2;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(numberOfThreads);
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failureCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            final long userId = (long) (i + 1);
            executor.submit(() -> {
                try {
                    latch.await(); // Đảm bảo cả 2 thread gọi đồng thời
                    User u = new User();
                    u.setId(userId);
                    Order o = new Order();
                    o.setId(10L + userId);

                    promotionService.redeemPromotion(testPromotion, u, o, BigDecimal.valueOf(10000));
                    successCount.incrementAndGet();
                } catch (BusinessException e) {
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown(); // Phát súng cho cả 2 thread chạy cùng lúc
        executor.shutdown();
        boolean finished = executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);
        verify(promotionRepository, org.mockito.Mockito.times(2)).incrementUsedCountAtomic(100L);
        verify(promotionUsageRepository, org.mockito.Mockito.times(1)).save(any());
    }

    // ---------- getPublicPromotions: danh sách mã cho khách chọn ở trang thanh toán ----------

    @AfterEach
    void tearDownSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Promotion usablePromotion(Long id, String code, BigDecimal minOrderAmount) {
        Promotion promotion = Promotion.builder()
                .code(code)
                .description("Mô tả " + code)
                .discountType(DiscountType.PERCENTAGE)
                .value(BigDecimal.valueOf(10))
                .maxDiscountAmount(BigDecimal.valueOf(30000))
                .minOrderAmount(minOrderAmount)
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(5))
                .maxUsage(10)
                .usedCount(0)
                .active(true)
                .build();
        promotion.setId(id);
        return promotion;
    }

    @Test
    @DisplayName("Danh sách công khai trả đúng các field khách cần, không lộ lượt dùng / id")
    void getPublicPromotions_mapsPublicFieldsOnly() {
        when(promotionRepository.findUsableAt(any(LocalDateTime.class)))
                .thenReturn(List.of(usablePromotion(100L, "BANHMYKING10", BigDecimal.valueOf(50000))));

        List<PublicPromotionResponse> result = promotionService.getPublicPromotions();

        assertThat(result).hasSize(1);
        PublicPromotionResponse promo = result.get(0);
        assertThat(promo.code()).isEqualTo("BANHMYKING10");
        assertThat(promo.description()).isEqualTo("Mô tả BANHMYKING10");
        assertThat(promo.discountType()).isEqualTo(DiscountType.PERCENTAGE);
        assertThat(promo.value()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(promo.maxDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(promo.minOrderAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        assertThat(promo.endsAt()).isNotNull();
    }

    @Test
    @DisplayName("Khách ẩn danh: không truy vấn lượt đã dùng, trả về mọi mã còn dùng được")
    void getPublicPromotions_anonymous_doesNotQueryUsage() {
        when(promotionRepository.findUsableAt(any(LocalDateTime.class)))
                .thenReturn(List.of(
                        usablePromotion(100L, "MA1", BigDecimal.valueOf(50000)),
                        usablePromotion(200L, "MA2", BigDecimal.ZERO)));

        List<PublicPromotionResponse> result = promotionService.getPublicPromotions();

        assertThat(result).extracting(PublicPromotionResponse::code).containsExactly("MA1", "MA2");
        verify(promotionUsageRepository, never()).findPromotionIdsByUserId(any());
    }

    @Test
    @DisplayName("Loại mã khách hiện tại đã dùng — không mời chọn thứ chắc chắn bị từ chối")
    void getPublicPromotions_excludesCodesAlreadyUsedByCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        when(promotionRepository.findUsableAt(any(LocalDateTime.class)))
                .thenReturn(List.of(
                        usablePromotion(100L, "DA_DUNG", BigDecimal.valueOf(50000)),
                        usablePromotion(200L, "CON_DUNG", BigDecimal.ZERO)));
        when(promotionUsageRepository.findPromotionIdsByUserId(1L)).thenReturn(List.of(100L));

        List<PublicPromotionResponse> result = promotionService.getPublicPromotions();

        assertThat(result).extracting(PublicPromotionResponse::code).containsExactly("CON_DUNG");
    }

    // ---------- getWallet: ví mã của khách ----------

    @Test
    @DisplayName("Ví mã: mã còn dùng được đánh dấu chưa dùng, các field đơn hàng để trống")
    void getWallet_availableItems_areMarkedUnusedWithoutOrderFields() {
        when(promotionRepository.findUsableAt(any(LocalDateTime.class)))
                .thenReturn(List.of(usablePromotion(100L, "CON_DUNG", BigDecimal.valueOf(50000))));
        when(promotionUsageRepository.findPromotionIdsByUserId(1L)).thenReturn(List.of());

        List<WalletPromotionResponse> wallet = promotionService.getWallet(1L);

        assertThat(wallet).hasSize(1);
        WalletPromotionResponse item = wallet.get(0);
        assertThat(item.code()).isEqualTo("CON_DUNG");
        assertThat(item.used()).isFalse();
        assertThat(item.usedAt()).isNull();
        assertThat(item.orderCode()).isNull();
        assertThat(item.discountApplied()).isNull();
    }

    @Test
    @DisplayName("Ví mã: mã đã dùng rời khỏi phần khả dụng và kèm đơn đã áp + số tiền đã giảm")
    void getWallet_usedCode_movesToUsedSectionWithOrderDetails() {
        Promotion usedPromo = usablePromotion(100L, "DA_DUNG", BigDecimal.valueOf(50000));
        when(promotionRepository.findUsableAt(any(LocalDateTime.class)))
                .thenReturn(List.of(usedPromo, usablePromotion(200L, "CON_DUNG", BigDecimal.ZERO)));
        when(promotionUsageRepository.findPromotionIdsByUserId(1L)).thenReturn(List.of(100L));

        Order order = new Order();
        order.setOrderCode("BMK-20260928-ABCDE");
        PromotionUsage usage = new PromotionUsage();
        usage.setPromotion(usedPromo);
        usage.setUser(testUser);
        usage.setOrder(order);
        usage.setDiscountApplied(BigDecimal.valueOf(25000));
        usage.setCreatedAt(LocalDateTime.now().minusDays(2));
        when(promotionUsageRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(usage));

        List<WalletPromotionResponse> wallet = promotionService.getWallet(1L);

        assertThat(wallet).extracting(WalletPromotionResponse::code)
                .containsExactly("CON_DUNG", "DA_DUNG");

        WalletPromotionResponse used = wallet.get(1);
        assertThat(used.used()).isTrue();
        assertThat(used.orderCode()).isEqualTo("BMK-20260928-ABCDE");
        assertThat(used.discountApplied()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(used.usedAt()).isNotNull();
    }

    @Test
    @DisplayName("Ví mã: mã đã dùng nhưng đã hết hiệu lực vẫn nằm trong ví (lịch sử không phụ thuộc findUsableAt)")
    void getWallet_usedCodeNoLongerUsable_stillListedAsUsed() {
        Promotion expired = usablePromotion(300L, "HET_HAN", BigDecimal.ZERO);
        // findUsableAt chỉ trả mã còn hiệu lực — mã đã dùng/đã hết hạn không nằm trong đó
        when(promotionRepository.findUsableAt(any(LocalDateTime.class))).thenReturn(List.of());
        when(promotionUsageRepository.findPromotionIdsByUserId(1L)).thenReturn(List.of(300L));

        Order order = new Order();
        order.setOrderCode("BMK-20260920-ZZZZZ");
        PromotionUsage usage = new PromotionUsage();
        usage.setPromotion(expired);
        usage.setUser(testUser);
        usage.setOrder(order);
        usage.setDiscountApplied(BigDecimal.valueOf(15000));
        when(promotionUsageRepository.findByUserIdWithDetails(1L)).thenReturn(List.of(usage));

        List<WalletPromotionResponse> wallet = promotionService.getWallet(1L);

        assertThat(wallet).hasSize(1);
        assertThat(wallet.get(0).code()).isEqualTo("HET_HAN");
        assertThat(wallet.get(0).used()).isTrue();
    }
}
