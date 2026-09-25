package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.PromotionUsage;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PromotionRepositoryTest {

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private PromotionUsageRepository promotionUsageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Khởi tạo thành công Entity Promotion và PromotionUsage với đầy đủ trường")
    void createEntities_success() {
        Promotion promotion = Promotion.builder()
                .code("SUMMER2026")
                .description("Giảm 20k cho đơn từ 50k")
                .discountType(DiscountType.FIXED_AMOUNT)
                .value(BigDecimal.valueOf(20000))
                .minOrderAmount(BigDecimal.valueOf(50000))
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(10))
                .maxUsage(100)
                .usedCount(0)
                .active(true)
                .build();

        Promotion savedPromotion = promotionRepository.save(promotion);
        assertThat(savedPromotion.getId()).isNotNull();

        User user = new User();
        user.setEmail("user1_promo@test.com");
        user.setPassword("password123");
        user.setFullName("User Test");
        user.setRole(RoleName.CUSTOMER);
        entityManager.persist(user);
        entityManager.flush();

        Order order = new Order();
        order.setOrderCode("BMK-20260910-00001");
        order.setUser(user);
        order.setStatus(OrderStatus.PENDING);
        order.setReceiverName("User Test");
        order.setReceiverPhone("0900000001");
        order.setShippingAddress("123 Test Street");
        order.setSubtotal(BigDecimal.valueOf(50000));
        order.setShippingFee(BigDecimal.valueOf(15000));
        order.setDiscountAmount(BigDecimal.valueOf(20000));
        order.setTotal(BigDecimal.valueOf(45000));
        entityManager.persist(order);
        entityManager.flush();

        PromotionUsage usage = PromotionUsage.builder()
                .promotion(savedPromotion)
                .user(user)
                .order(order)
                .discountApplied(BigDecimal.valueOf(20000))
                .build();

        PromotionUsage savedUsage = promotionUsageRepository.save(usage);

        assertThat(savedUsage.getId()).isNotNull();
        assertThat(savedUsage.getPromotion().getCode()).isEqualTo("SUMMER2026");
        assertThat(savedUsage.getUser().getEmail()).isEqualTo("user1_promo@test.com");
        assertThat(savedUsage.getDiscountApplied()).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    @DisplayName("Atomic redemption: Tăng lượt sử dụng thành công khi used_count < max_usage (affected rows = 1)")
    void incrementUsedCountAtomic_whenUsedCountLessThanMaxUsage_returnsOne() {
        Promotion promotion = Promotion.builder()
                .code("ATOMIC10")
                .description("Mã thử nghiệm atomic 10 lượt")
                .discountType(DiscountType.PERCENTAGE)
                .value(BigDecimal.valueOf(10))
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(1))
                .maxUsage(10)
                .usedCount(5)
                .active(true)
                .build();

        entityManager.persist(promotion);
        entityManager.flush();
        Long promoId = promotion.getId();

        int affectedRows = promotionRepository.incrementUsedCountAtomic(promoId);
        entityManager.clear(); // Clear persistence context to read updated data from DB

        assertThat(affectedRows).isEqualTo(1);

        Optional<Promotion> updatedPromo = promotionRepository.findById(promoId);
        assertThat(updatedPromo).isPresent();
        assertThat(updatedPromo.get().getUsedCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("Atomic redemption: Trả về 0 dòng bị ảnh hưởng khi used_count đã bằng max_usage")
    void incrementUsedCountAtomic_whenUsedCountEqualsMaxUsage_returnsZero() {
        Promotion promotion = Promotion.builder()
                .code("EXHAUSTED")
                .description("Mã đã dùng hết 5/5 lượt")
                .discountType(DiscountType.FIXED_AMOUNT)
                .value(BigDecimal.valueOf(10000))
                .startsAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(1))
                .maxUsage(5)
                .usedCount(5)
                .active(true)
                .build();

        entityManager.persist(promotion);
        entityManager.flush();
        Long promoId = promotion.getId();

        int affectedRows = promotionRepository.incrementUsedCountAtomic(promoId);
        entityManager.clear();

        assertThat(affectedRows).isEqualTo(0);

        Optional<Promotion> updatedPromo = promotionRepository.findById(promoId);
        assertThat(updatedPromo).isPresent();
        assertThat(updatedPromo.get().getUsedCount()).isEqualTo(5);
    }
}
