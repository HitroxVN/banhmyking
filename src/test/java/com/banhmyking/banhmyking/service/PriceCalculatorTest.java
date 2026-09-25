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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceCalculatorTest {

    private final PriceCalculator priceCalculator = new PriceCalculator();

    private Cart cart;
    private Product product1;
    private Product product2;
    private ProductOption option1;
    private ProductOption option2;

    @BeforeEach
    void setUp() {
        product1 = new Product();
        product1.setId(1L);
        product1.setName("Bánh mì Pate");
        product1.setPrice(BigDecimal.valueOf(30000));

        product2 = new Product();
        product2.setId(2L);
        product2.setName("Trà sữa");
        product2.setPrice(BigDecimal.valueOf(25000));

        option1 = new ProductOption();
        option1.setId(10L);
        option1.setName("Thêm chả");
        option1.setExtraPrice(BigDecimal.valueOf(8000));

        option2 = new ProductOption();
        option2.setId(11L);
        option2.setName("Trân châu trắng");
        option2.setExtraPrice(BigDecimal.valueOf(5000));

        cart = new Cart();
        CartItem item1 = new CartItem();
        item1.setProduct(product1);
        item1.setQuantity(2); // 2 x (30k + 8k) = 76k

        CartItemOption itemOpt1 = new CartItemOption();
        itemOpt1.setProductOption(option1);
        item1.getSelectedOptions().add(itemOpt1);
        cart.getItems().add(item1);
    }

    @Nested
    @DisplayName("1. Tính Subtotal và Cart Item cơ bản")
    class SubtotalCalculationTests {

        @Test
        @DisplayName("Tính tiền chuẩn với 1 món có topping và số lượng 2")
        void calculate_singleItemWithOption() {
            // (30k + 8k) * 2 = 76.000đ; ship mặc định = 15.000đ; total = 91.000đ
            PriceBreakdown breakdown = priceCalculator.calculate(cart, null);

            assertThat(breakdown.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(76000));
            assertThat(breakdown.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(91000));
        }

        @Test
        @DisplayName("Tính tiền giỏ hàng gồm nhiều món với nhiều topping khác nhau")
        void calculate_multiItemsWithMultipleOptions() {
            // Thêm món thứ 2: Trà sữa (25k) + Trân châu (5k), quantity = 3 -> 3 * 30k = 90.000đ
            CartItem item2 = new CartItem();
            item2.setProduct(product2);
            item2.setQuantity(3);

            CartItemOption itemOpt2 = new CartItemOption();
            itemOpt2.setProductOption(option2);
            item2.getSelectedOptions().add(itemOpt2);
            cart.getItems().add(item2);

            // Subtotal = 76.000 + 90.000 = 166.000đ
            PriceBreakdown breakdown = priceCalculator.calculate(cart, null);

            assertThat(breakdown.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(166000));
            assertThat(breakdown.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(181000));
        }

        @Test
        @DisplayName("Giỏ hàng rỗng hoặc null -> subtotal = 0, total = shippingFee")
        void calculate_emptyOrNullCart() {
            Cart emptyCart = new Cart();
            PriceBreakdown b1 = priceCalculator.calculate(emptyCart, null);
            assertThat(b1.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(b1.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(15000));

            PriceBreakdown b2 = priceCalculator.calculate(null, null);
            assertThat(b2.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(b2.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        }

        @Test
        @DisplayName("Item có quantity null thì mặc định tính là 1; product/option giá null tính là 0")
        void calculate_nullQuantityAndNullPrice_shouldDefaultSafely() {
            Cart customCart = new Cart();
            CartItem item = new CartItem();
            Product p = new Product();
            p.setPrice(null);
            item.setProduct(p);
            item.setQuantity(null); // defaults to 1

            CartItemOption opt = new CartItemOption();
            ProductOption po = new ProductOption();
            po.setExtraPrice(null);
            opt.setProductOption(po);
            item.getSelectedOptions().add(opt);

            customCart.getItems().add(item);

            PriceBreakdown breakdown = priceCalculator.calculate(customCart, null);
            assertThat(breakdown.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        }
    }

    @Nested
    @DisplayName("2. Phí vận chuyển tùy chỉnh (Custom Shipping Fee)")
    class CustomShippingFeeTests {

        @Test
        @DisplayName("Truyền phí giao hàng tùy chỉnh 25.000đ -> total = subtotal + 25.000")
        void calculate_withCustomShippingFee() {
            PriceBreakdown breakdown = priceCalculator.calculate(cart, null, BigDecimal.valueOf(25000));

            assertThat(breakdown.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(76000));
            assertThat(breakdown.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(25000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(101000));
        }

        @Test
        @DisplayName("Truyền phí giao hàng = 0đ (miễn phí vận chuyển từ DeliveryFeeCalculator)")
        void calculate_withZeroShippingFee() {
            PriceBreakdown breakdown = priceCalculator.calculate(cart, null, BigDecimal.ZERO);

            assertThat(breakdown.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(76000));
        }

        @Test
        @DisplayName("Truyền phí giao hàng null -> Tự động fallback về DEFAULT_SHIPPING_FEE (15.000đ)")
        void calculate_withNullShippingFee_shouldFallbackToDefault() {
            PriceBreakdown breakdown = priceCalculator.calculate(cart, null, null);

            assertThat(breakdown.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(91000));
        }
    }

    @Nested
    @DisplayName("3. Tính toán Khuyến mãi theo các DiscountType")
    class PromotionDiscountCalculationTests {

        private Promotion createBasePromotion(DiscountType discountType, BigDecimal value) {
            Promotion promo = new Promotion();
            promo.setCode("PROMO_TEST");
            promo.setActive(true);
            promo.setDiscountType(discountType);
            promo.setValue(value);
            promo.setMinOrderAmount(BigDecimal.valueOf(50000));
            promo.setStartsAt(LocalDateTime.now().minusDays(1));
            promo.setEndsAt(LocalDateTime.now().plusDays(1));
            return promo;
        }

        @Test
        @DisplayName("PERCENTAGE: 10% của 76.000đ có trần tối đa 5.000đ -> Bị chặn ở trần 5.000đ")
        void percentageDiscount_cappedAtMaxDiscount() {
            Promotion promo = createBasePromotion(DiscountType.PERCENTAGE, BigDecimal.valueOf(10));
            promo.setMaxDiscountAmount(BigDecimal.valueOf(5000)); // 10% của 76k = 7.6k > 5k

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(5000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(86000));
        }

        @Test
        @DisplayName("PERCENTAGE: 10% của 76.000đ không có trần (maxDiscountAmount = null) -> Giảm đủ 7.600đ")
        void percentageDiscount_withoutMaxDiscount() {
            Promotion promo = createBasePromotion(DiscountType.PERCENTAGE, BigDecimal.valueOf(10));
            promo.setMaxDiscountAmount(null);

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

            // 10% * 76.000 = 7.600đ. Total = 76k + 15k - 7.6k = 83.400đ
            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(7600));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(83400));
        }

        @Test
        @DisplayName("PERCENTAGE: 100% giảm giá đơn hàng -> Subtotal giảm hết, chỉ còn phí ship")
        void percentageDiscount_100Percent() {
            Promotion promo = createBasePromotion(DiscountType.PERCENTAGE, BigDecimal.valueOf(100));

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(76000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(15000)); // còn phí ship
        }

        @Test
        @DisplayName("FIXED_AMOUNT: Giảm 20.000đ (nhỏ hơn subtotal 76.000đ)")
        void fixedAmountDiscount_normal() {
            Promotion promo = createBasePromotion(DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(20000));

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(71000)); // 76k + 15k - 20k
        }

        @Test
        @DisplayName("FIXED_AMOUNT: Giảm 100.000đ (lớn hơn subtotal 76.000đ) -> Chỉ giảm tối đa bằng subtotal (76.000đ)")
        void fixedAmountDiscount_greaterThanSubtotal_shouldCapAtSubtotal() {
            Promotion promo = createBasePromotion(DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(100000));

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

            // Giảm tối đa bằng subtotal (76.000đ), không giảm lẹm vào phí ship
            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(76000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(15000)); // 76k + 15k - 76k = 15k
        }

        @Test
        @DisplayName("FREE_SHIP: Giá trị voucher free ship lớn hơn phí ship -> Chỉ giảm bằng đúng phí ship")
        void freeShipDiscount_greaterThanShippingFee_shouldCapAtShippingFee() {
            Promotion promo = createBasePromotion(DiscountType.FREE_SHIP, BigDecimal.valueOf(30000));

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo, BigDecimal.valueOf(15000));

            // Chỉ giảm tối đa 15.000đ phí ship
            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(15000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(76000));
        }

        @Test
        @DisplayName("FREE_SHIP: Voucher giảm phí ship một phần (giảm 10.000đ trên phí ship 15.000đ)")
        void freeShipDiscount_partial() {
            Promotion promo = createBasePromotion(DiscountType.FREE_SHIP, BigDecimal.valueOf(10000));

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo, BigDecimal.valueOf(15000));

            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(81000)); // 76k + 15k - 10k
        }

        @Test
        @DisplayName("Voucher có discountType null hoặc value null -> Giảm giá = 0đ")
        void discount_withNullTypeOrValue_shouldBeZero() {
            Promotion promo = createBasePromotion(null, null);

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(breakdown.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(91000));
        }
    }

    @Nested
    @DisplayName("4. Kiểm tra điều kiện áp dụng Promotion (Validation & Exceptions)")
    class PromotionValidationTests {

        private Promotion createPromo() {
            Promotion promo = new Promotion();
            promo.setCode("VALID_CODE");
            promo.setActive(true);
            promo.setDiscountType(DiscountType.FIXED_AMOUNT);
            promo.setValue(BigDecimal.valueOf(10000));
            promo.setMinOrderAmount(BigDecimal.valueOf(50000));
            promo.setStartsAt(LocalDateTime.now().minusDays(1));
            promo.setEndsAt(LocalDateTime.now().plusDays(1));
            return promo;
        }

        @Test
        @DisplayName("Mã khuyến mãi không active (active = false) -> Báo lỗi BUSINESS_ERROR")
        void validate_inactivePromotion_shouldThrowException() {
            Promotion promo = createPromo();
            promo.setActive(false);

            assertThatThrownBy(() -> priceCalculator.calculate(cart, promo))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Mã khuyến mãi hiện không kích hoạt");
        }

        @Test
        @DisplayName("Mã khuyến mãi chưa đến ngày bắt đầu (startsAt trong tương lai) -> Báo lỗi")
        void validate_notStartedPromotion_shouldThrowException() {
            Promotion promo = createPromo();
            promo.setStartsAt(LocalDateTime.now().plusHours(2));

            assertThatThrownBy(() -> priceCalculator.calculate(cart, promo))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Mã khuyến mãi chưa đến thời gian áp dụng");
        }

        @Test
        @DisplayName("Mã khuyến mãi đã hết hạn (endsAt trong quá khứ) -> Báo lỗi")
        void validate_expiredPromotion_shouldThrowException() {
            Promotion promo = createPromo();
            promo.setEndsAt(LocalDateTime.now().minusMinutes(5));

            assertThatThrownBy(() -> priceCalculator.calculate(cart, promo))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Mã khuyến mãi đã hết hạn sử dụng");
        }

        @Test
        @DisplayName("Mã khuyến mãi đã hết lượt sử dụng (usedCount >= maxUsage) -> Báo lỗi")
        void validate_maxUsageReached_shouldThrowException() {
            Promotion promo = createPromo();
            promo.setMaxUsage(100);
            promo.setUsedCount(100);

            assertThatThrownBy(() -> priceCalculator.calculate(cart, promo))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Mã khuyến mãi đã hết lượt sử dụng");
        }

        @Test
        @DisplayName("Đơn hàng vừa đúng ngưỡng minOrderAmount (subtotal == minOrderAmount) -> Hợp lệ, áp dụng thành công")
        void validate_subtotalEqualsMinOrderAmount_shouldSucceed() {
            Promotion promo = createPromo();
            promo.setMinOrderAmount(BigDecimal.valueOf(76000)); // Đúng bằng 76.000đ

            PriceBreakdown breakdown = priceCalculator.calculate(cart, promo);

            assertThat(breakdown.getDiscountAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        }

        @Test
        @DisplayName("Đơn hàng thiếu 1đ so với minOrderAmount (subtotal < minOrderAmount) -> Bị từ chối")
        void validate_subtotalBelowMinOrderAmount_shouldThrowException() {
            Promotion promo = createPromo();
            promo.setMinOrderAmount(BigDecimal.valueOf(76001)); // Đơn chỉ có 76.000đ

            assertThatThrownBy(() -> priceCalculator.calculate(cart, promo))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("chưa đạt giá trị tối thiểu");
        }
    }
}
