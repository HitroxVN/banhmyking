package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.enums.ProductType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductPricingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);
    private final ProductPricing pricing = new ProductPricing(
            Clock.fixed(NOW.atZone(TimeConfig.VIETNAM).toInstant(), TimeConfig.VIETNAM));

    @Test
    @DisplayName("now() lấy giờ Việt Nam từ Clock")
    void nowComesFromClock() {
        assertThat(pricing.now()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Giá KM không giới hạn thời gian: đang KM, gạch giá gốc, giảm 16%")
    void saleWithoutWindowIsActive() {
        Product p = single(1L, "30000", "25000", null, null);

        assertThat(pricing.isSaleActive(p, NOW)).isTrue();
        assertThat(pricing.effectivePrice(p, NOW)).isEqualByComparingTo("25000");
        assertThat(pricing.compareAtPrice(p, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.discountPercent(p, NOW)).isEqualTo(16);
    }

    @Test
    @DisplayName("Trước saleStartsAt: chưa KM, không gạch giá")
    void saleNotStartedYet() {
        Product p = single(1L, "30000", "25000", NOW.plusMinutes(1), null);

        assertThat(pricing.isSaleActive(p, NOW)).isFalse();
        assertThat(pricing.effectivePrice(p, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.compareAtPrice(p, NOW)).isNull();
        assertThat(pricing.discountPercent(p, NOW)).isNull();
    }

    @Test
    @DisplayName("Đúng mốc saleStartsAt: đã KM (now ≥ starts)")
    void saleStartsExactlyNow() {
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", NOW, null), NOW)).isTrue();
    }

    @Test
    @DisplayName("Đúng mốc saleEndsAt: đã hết KM (now < ends là sai)")
    void saleEndsExactlyNowIsOver() {
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", null, NOW), NOW)).isFalse();
    }

    @Test
    @DisplayName("Trước saleEndsAt 1 giây: còn KM; sau saleEndsAt: hết KM")
    void saleAroundEnd() {
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", null, NOW.plusSeconds(1)), NOW)).isTrue();
        assertThat(pricing.isSaleActive(single(1L, "30000", "25000", NOW.minusDays(2), NOW.minusDays(1)), NOW))
                .isFalse();
    }

    @Test
    @DisplayName("Không có salePrice: giá hiệu lực = giá gốc")
    void noSalePrice() {
        Product p = single(1L, "30000", null, null, null);

        assertThat(pricing.isSaleActive(p, NOW)).isFalse();
        assertThat(pricing.effectivePrice(p, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.originalPrice(p)).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("Combo: giá gốc = Σ giá GỐC thành phần × số lượng (bỏ qua giá KM của thành phần)")
    void comboOriginalPriceUsesComponentListPrices() {
        Product banhMi = single(1L, "30000", "20000", null, null); // thành phần đang KM
        Product coffee = single(2L, "20000", null, null, null);
        Product combo = combo(9L, "45000", banhMi, 1, coffee, 2);
        combo.setSalePrice(new BigDecimal("1000")); // combo không bao giờ dùng giá KM

        assertThat(pricing.originalPrice(combo)).isEqualByComparingTo("70000");
        assertThat(pricing.isSaleActive(combo, NOW)).isFalse();
        assertThat(pricing.effectivePrice(combo, NOW)).isEqualByComparingTo("45000");
        assertThat(pricing.compareAtPrice(combo, NOW)).isEqualByComparingTo("70000");
        assertThat(pricing.discountPercent(combo, NOW)).isEqualTo(35);
    }

    @Test
    @DisplayName("Combo không rẻ hơn tổng giá lẻ: không gạch giá, không % giảm")
    void comboNotCheaperHasNoCompareAt() {
        Product combo = combo(9L, "80000", single(1L, "30000", null, null, null), 1,
                single(2L, "20000", null, null, null), 2);

        assertThat(pricing.compareAtPrice(combo, NOW)).isNull();
        assertThat(pricing.discountPercent(combo, NOW)).isNull();
    }

    @Test
    @DisplayName("Dòng giỏ: đơn giá = giá hiệu lực + topping; tiết kiệm = chênh giá × số lượng")
    void cartLinePricesUseEffectivePricePlusToppings() {
        CartItem item = new CartItem();
        item.setProduct(single(1L, "30000", "25000", null, null));
        item.setQuantity(2);
        ProductOption pate = new ProductOption();
        pate.setExtraPrice(new BigDecimal("5000"));
        CartItemOption selected = new CartItemOption();
        selected.setProductOption(pate);
        item.getSelectedOptions().add(selected);

        assertThat(pricing.optionsExtra(item)).isEqualByComparingTo("5000");
        assertThat(pricing.unitPrice(item, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.lineTotal(item, NOW)).isEqualByComparingTo("60000");
        assertThat(pricing.lineSavings(item, NOW)).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("Dòng giỏ không ưu đãi: tiết kiệm = 0; số lượng null tính là 1")
    void cartLineWithoutSale() {
        CartItem item = new CartItem();
        item.setProduct(single(1L, "30000", null, null, null));
        item.setQuantity(null);

        assertThat(pricing.lineTotal(item, NOW)).isEqualByComparingTo("30000");
        assertThat(pricing.lineSavings(item, NOW)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("listPriceTotal = Σ giá niêm yết × số lượng, bỏ qua giá KM; rỗng/null = 0")
    void listPriceTotalSumsListPrices() {
        Map<Product, Integer> items = new LinkedHashMap<>();
        items.put(single(1L, "30000", "20000", null, null), 2);
        items.put(single(2L, "20000", null, null, null), 1);
        items.put(single(3L, "10000", null, null, null), null);

        assertThat(pricing.listPriceTotal(items)).isEqualByComparingTo("90000");
        assertThat(pricing.listPriceTotal(Map.of())).isEqualByComparingTo("0");
        assertThat(pricing.listPriceTotal(null)).isEqualByComparingTo("0");
    }

    private static Product single(Long id, String price, String salePrice, LocalDateTime starts, LocalDateTime ends) {
        Product p = new Product();
        p.setId(id);
        p.setName("Món " + id);
        p.setPrice(new BigDecimal(price));
        p.setSalePrice(salePrice == null ? null : new BigDecimal(salePrice));
        p.setSaleStartsAt(starts);
        p.setSaleEndsAt(ends);
        return p;
    }

    private static Product combo(Long id, String price, Product first, int firstQty, Product second, int secondQty) {
        Product combo = new Product();
        combo.setId(id);
        combo.setName("Combo " + id);
        combo.setProductType(ProductType.COMBO);
        combo.setPrice(new BigDecimal(price));
        combo.setComboItems(List.of(item(combo, first, firstQty), item(combo, second, secondQty)));
        return combo;
    }

    private static ComboItem item(Product combo, Product component, int quantity) {
        ComboItem item = new ComboItem();
        item.setId(new ComboItemId(combo.getId(), component.getId()));
        item.setCombo(combo);
        item.setComponent(component);
        item.setQuantity(quantity);
        return item;
    }
}
