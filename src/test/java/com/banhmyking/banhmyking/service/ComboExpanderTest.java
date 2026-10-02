package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.ComboItemId;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.enums.ProductType;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComboExpanderTest {

    @Test
    @DisplayName("Combo × 2 + món lẻ trùng thành phần → cộng dồn theo món lẻ")
    void expandsComboAndMergesWithSingleLines() {
        Product banhMi = single(1L, "Bánh mì");
        Product coffee = single(2L, "Cà phê sữa đá");
        Product combo = combo(9L, banhMi, 1, coffee, 2);
        Map<Product, Integer> lines = new LinkedHashMap<>();
        lines.put(combo, 2);
        lines.put(banhMi, 1);

        Map<Product, Integer> demand = ComboExpander.expand(lines);

        assertThat(demand).hasSize(2);
        assertThat(demand.get(banhMi)).isEqualTo(3);   // 1 × 2 + 1
        assertThat(demand.get(coffee)).isEqualTo(4);   // 2 × 2
        assertThat(demand.keySet()).containsExactly(banhMi, coffee);
    }

    @Test
    @DisplayName("Hai đối tượng Product cùng id được gộp chung một dòng")
    void mergesByIdNotByInstance() {
        Product first = single(1L, "Bánh mì");
        Product sameId = single(1L, "Bánh mì");
        Map<Product, Integer> lines = new LinkedHashMap<>();
        lines.put(first, 1);
        lines.put(sameId, 2);

        Map<Product, Integer> demand = ComboExpander.expand(lines);

        assertThat(demand).containsExactly(Map.entry(first, 3));
    }

    @Test
    @DisplayName("Món lẻ không id (chưa lưu) vẫn giữ nguyên, không lỗi")
    void keepsProductsWithoutId() {
        Product transientProduct = new Product();
        Map<Product, Integer> demand = ComboExpander.expand(Map.of(transientProduct, 2));

        assertThat(demand).containsExactly(Map.entry(transientProduct, 2));
    }

    @Test
    @DisplayName("Combo còn bán ở mức chuỗi chỉ khi chính combo và mọi thành phần đang bán, chưa xoá")
    void chainAvailability() {
        Product banhMi = single(1L, "Bánh mì");
        Product coffee = single(2L, "Cà phê sữa đá");
        Product combo = combo(9L, banhMi, 1, coffee, 1);

        assertThat(ComboExpander.isChainAvailable(banhMi)).isTrue();
        assertThat(ComboExpander.isChainAvailable(combo)).isTrue();

        coffee.setAvailable(false);
        assertThat(ComboExpander.isChainAvailable(combo)).isFalse();

        coffee.setAvailable(true);
        combo.setAvailable(false);
        assertThat(ComboExpander.isChainAvailable(combo)).isFalse();

        combo.setAvailable(true);
        banhMi.setDeleted(true);
        assertThat(ComboExpander.isChainAvailable(combo)).isFalse();
        assertThat(ComboExpander.isChainAvailable(banhMi)).isFalse();

        Product empty = combo(10L, single(3L, "x"), 1, single(4L, "y"), 1);
        empty.setComboItems(List.of());
        assertThat(ComboExpander.isChainAvailable(empty)).isFalse();
        assertThat(ComboExpander.isChainAvailable(null)).isFalse();
    }

    private static Product single(Long id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("20000"));
        p.setAvailable(true);
        return p;
    }

    private static Product combo(Long id, Product first, int firstQty, Product second, int secondQty) {
        Product combo = single(id, "Combo " + id);
        combo.setProductType(ProductType.COMBO);
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
