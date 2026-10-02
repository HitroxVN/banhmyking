package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.Product;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hàm dùng chung DUY NHẤT để quy giỏ/đơn về nhu cầu theo MÓN LẺ (spec combo-sale §4.2) và để biết
 * combo còn bán ở mức chuỗi (§4.3). Tĩnh, không phụ thuộc bean — tồn kho, báo giá, tạo đơn đều gọi.
 */
public final class ComboExpander {

    private ComboExpander() {
    }

    /**
     * Dòng SINGLE giữ nguyên; dòng COMBO × q → mỗi thành phần × (quantity × q). Cùng món (theo id) thì
     * cộng dồn. Thứ tự theo lần xuất hiện đầu tiên; khoá là đối tượng Product gặp đầu tiên.
     */
    public static Map<Product, Integer> expand(Map<Product, Integer> lines) {
        Map<Object, Product> products = new LinkedHashMap<>();
        Map<Object, Integer> quantities = new LinkedHashMap<>();
        for (Map.Entry<Product, Integer> line : lines.entrySet()) {
            Product product = line.getKey();
            Integer quantity = line.getValue();
            if (product == null || quantity == null) {
                continue;
            }
            if (product.isCombo()) {
                for (ComboItem item : product.getComboItems()) {
                    if (item.getComponent() != null) {
                        add(products, quantities, item.getComponent(), item.getQuantity() * quantity);
                    }
                }
            } else {
                add(products, quantities, product, quantity);
            }
        }
        Map<Product, Integer> demand = new LinkedHashMap<>();
        products.forEach((key, product) -> demand.put(product, quantities.get(key)));
        return demand;
    }

    /** Mức chuỗi (thực đơn, thêm giỏ, tạo đơn): combo bán được khi chính nó và mọi thành phần đang bán. */
    public static boolean isChainAvailable(Product product) {
        if (product == null || product.isDeleted() || !product.isAvailable()) {
            return false;
        }
        if (!product.isCombo()) {
            return true;
        }
        List<ComboItem> items = product.getComboItems();
        return !items.isEmpty() && items.stream()
                .map(ComboItem::getComponent)
                .allMatch(component -> component != null && component.isAvailable() && !component.isDeleted());
    }

    private static void add(Map<Object, Product> products, Map<Object, Integer> quantities,
                            Product product, int quantity) {
        Object key = product.getId() != null ? product.getId() : product;
        products.putIfAbsent(key, product);
        quantities.merge(key, quantity, Integer::sum);
    }
}
