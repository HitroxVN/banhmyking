package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.ComboItem;
import com.banhmyking.banhmyking.entity.Product;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Nguồn sự thật DUY NHẤT của giá bán (spec combo-sale §3): giá KM theo thời gian, giá gốc của combo,
 * giá gạch, đơn giá dòng giỏ. PriceCalculator, giỏ, đơn, báo giá và thực đơn đều gọi qua đây —
 * không nơi nào tự cộng giá lại.
 */
@Component
public class ProductPricing {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final Clock clock;

    public ProductPricing(Clock clock) {
        this.clock = clock;
    }

    /** Giờ Việt Nam hiện tại — mốc dùng chung cho một lần tính (giỏ, báo giá) hoặc mốc chốt giá (tạo đơn). */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** Món lẻ có salePrice và {@code now} nằm trong [saleStartsAt, saleEndsAt); NULL = không giới hạn phía đó. */
    public boolean isSaleActive(Product product, LocalDateTime now) {
        if (product == null || product.isCombo() || product.getSalePrice() == null) {
            return false;
        }
        if (product.getSaleStartsAt() != null && now.isBefore(product.getSaleStartsAt())) {
            return false;
        }
        return product.getSaleEndsAt() == null || now.isBefore(product.getSaleEndsAt());
    }

    /** Giá bán 1 phần chưa gồm topping: giá KM khi đang KM, ngược lại giá niêm yết (combo: giá combo). */
    public BigDecimal effectivePrice(Product product, LocalDateTime now) {
        if (product == null) {
            return BigDecimal.ZERO;
        }
        if (isSaleActive(product, now)) {
            return product.getSalePrice();
        }
        return nullToZero(product.getPrice());
    }

    /** Món lẻ: giá niêm yết. Combo: Σ giá GỐC thành phần × số lượng (không dùng giá KM của thành phần). */
    public BigDecimal originalPrice(Product product) {
        if (product == null) {
            return BigDecimal.ZERO;
        }
        if (!product.isCombo()) {
            return nullToZero(product.getPrice());
        }
        Map<Product, Integer> components = new java.util.LinkedHashMap<>();
        for (ComboItem item : product.getComboItems()) {
            if (item.getComponent() != null) {
                components.merge(item.getComponent(), item.getQuantity(), Integer::sum);
            }
        }
        return listPriceTotal(components);
    }

    /** Σ giá niêm yết × số lượng (bỏ qua giá KM); null-safe, số lượng null tính là 1. */
    public BigDecimal listPriceTotal(Map<Product, Integer> items) {
        BigDecimal sum = BigDecimal.ZERO;
        if (items == null) {
            return sum;
        }
        for (Map.Entry<Product, Integer> entry : items.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            int quantity = entry.getValue() != null ? entry.getValue() : 1;
            sum = sum.add(nullToZero(entry.getKey().getPrice()).multiply(BigDecimal.valueOf(quantity)));
        }
        return sum;
    }

    /** Giá gạch = giá gốc khi giá gốc lớn hơn giá đang bán; null = không gạch giá. */
    public BigDecimal compareAtPrice(Product product, LocalDateTime now) {
        BigDecimal original = originalPrice(product);
        return original.compareTo(effectivePrice(product, now)) > 0 ? original : null;
    }

    /** Làm tròn xuống (compareAt − effective) / compareAt × 100; chỉ để hiển thị. */
    public Integer discountPercent(Product product, LocalDateTime now) {
        BigDecimal compareAt = compareAtPrice(product, now);
        if (compareAt == null || compareAt.signum() <= 0) {
            return null;
        }
        return compareAt.subtract(effectivePrice(product, now))
                .multiply(HUNDRED)
                .divide(compareAt, 0, RoundingMode.DOWN)
                .intValue();
    }

    /** Tổng phụ phí topping của dòng giỏ (null-safe). */
    public BigDecimal optionsExtra(CartItem item) {
        BigDecimal extra = BigDecimal.ZERO;
        if (item.getSelectedOptions() != null) {
            for (CartItemOption option : item.getSelectedOptions()) {
                if (option.getProductOption() != null && option.getProductOption().getExtraPrice() != null) {
                    extra = extra.add(option.getProductOption().getExtraPrice());
                }
            }
        }
        return extra;
    }

    /** Đơn giá dòng giỏ = giá hiệu lực + topping (combo không có topping). */
    public BigDecimal unitPrice(CartItem item, LocalDateTime now) {
        return effectivePrice(item.getProduct(), now).add(optionsExtra(item));
    }

    public BigDecimal lineTotal(CartItem item, LocalDateTime now) {
        return unitPrice(item, now).multiply(BigDecimal.valueOf(quantityOf(item)));
    }

    /** Tiết kiệm của dòng = (giá gốc − giá hiệu lực) × số lượng, không âm. */
    public BigDecimal lineSavings(CartItem item, LocalDateTime now) {
        BigDecimal perUnit = originalPrice(item.getProduct()).subtract(effectivePrice(item.getProduct(), now));
        return perUnit.signum() > 0 ? perUnit.multiply(BigDecimal.valueOf(quantityOf(item))) : BigDecimal.ZERO;
    }

    private static int quantityOf(CartItem item) {
        return item.getQuantity() != null ? item.getQuantity() : 1;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
