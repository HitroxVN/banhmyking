package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.order.PriceBreakdown;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceCalculatorTest {

    private final PriceCalculator priceCalculator = new PriceCalculator();

    private Cart cart;
    private Product product;
    private ProductOption option;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Bánh mì Pate");
        product.setPrice(BigDecimal.valueOf(30000));

        option = new ProductOption();
        option.setId(10L);
        option.setName("Thêm chả");
        option.setExtraPrice(BigDecimal.valueOf(8000));

        cart = new Cart();
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(2); // 2 phần

        CartItemOption itemOpt = new CartItemOption();
        itemOpt.setProductOption(option);
        item.getSelectedOptions().add(itemOpt);

        cart.getItems().add(item);
    }

    @Test
    @DisplayName("AC 5: Tính tiền chuẩn khi không có mã khuyến mãi (total = subtotal + shippingFee)")
    void calculate_withoutPromotion_shouldCalculateStandardTotals() {
        // Đơn giá = 30.000 + 8.000 = 38.000. Số lượng = 2 -> Subtotal = 76.000. Phí ship = 15.000 -> Total = 91.000
        PriceBreakdown breakdown = priceCalculator.calculate(cart, null);

        assertThat(breakdown.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(76000));
        assertThat(breakdown.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(91000));
    }

    @Test
    @DisplayName("AC 5: Tính tiền với mã giảm giá PERCENTAGE có trần tối đa")
    void calculate_withPercentagePromotion_shouldApplyDiscount() {
        Promotion promo = new Promotion();
        promo.setCode("GIAM10");
        promo.setActive(true);
        promo.setDiscountType(DiscountType.PERCENTAGE);
        promo.setValue(BigDecimal.valueOf(10)); // 10%
        promo.setMaxDiscountAmount(BigDecimal.valueOf(5000)); // Trần 5.000đ (dù 10% của 76.000 là 7.600đ)
        promo.setMinOrderAmount(BigDecimal.valueOf(50000));
        promo.setStartsAt(LocalDateTime.now().minusDays(1));
        promo.setEndsAt(LocalDateTime.now().plusDays(1));

        PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

        // Giảm tối đa 5.000đ -> Total = 76.000 + 15.000 - 5.000 = 86.000đ
        assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(86000));
    }

    @Test
    @DisplayName("AC 5: Tính tiền với mã giảm giá FIXED_AMOUNT")
    void calculate_withFixedAmountPromotion_shouldApplyDiscount() {
        Promotion promo = new Promotion();
        promo.setCode("GIAM10K");
        promo.setActive(true);
        promo.setDiscountType(DiscountType.FIXED_AMOUNT);
        promo.setValue(BigDecimal.valueOf(10000)); // Giảm 10.000đ
        promo.setMinOrderAmount(BigDecimal.valueOf(30000));
        promo.setStartsAt(LocalDateTime.now().minusDays(1));
        promo.setEndsAt(LocalDateTime.now().plusDays(1));

        PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

        // Total = 76.000 + 15.000 - 10.000 = 81.000đ
        assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(81000));
    }

    @Test
    @DisplayName("Chặn áp dụng mã khi đơn hàng chưa đạt giá trị tối thiểu minOrderAmount")
    void calculate_whenSubtotalBelowMinOrderAmount_shouldThrowBusinessException() {
        Promotion promo = new Promotion();
        promo.setCode("GIAM20K");
        promo.setActive(true);
        promo.setDiscountType(DiscountType.FIXED_AMOUNT);
        promo.setValue(BigDecimal.valueOf(20000));
        promo.setMinOrderAmount(BigDecimal.valueOf(100000)); // Yêu cầu 100.000đ mà đơn chỉ có 76.000đ
        promo.setStartsAt(LocalDateTime.now().minusDays(1));
        promo.setEndsAt(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> priceCalculator.calculate(cart, promo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa đạt giá trị tối thiểu");
    }
}
