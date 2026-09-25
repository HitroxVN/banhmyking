package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.order.PriceBreakdown;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Component
public class PriceCalculator {

    public static final BigDecimal DEFAULT_SHIPPING_FEE = BigDecimal.valueOf(15000);

    /**
     * Tính toán subtotal, shippingFee, discountAmount và total cho giỏ hàng.
     */
    public PriceBreakdown calculate(Cart cart, Promotion promotion) {
        return calculate(cart, promotion, DEFAULT_SHIPPING_FEE);
    }

    public BigDecimal calculateSubtotal(Cart cart) {
        BigDecimal subtotal = BigDecimal.ZERO;
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                BigDecimal basePrice = item.getProduct() != null && item.getProduct().getPrice() != null
                        ? item.getProduct().getPrice()
                        : BigDecimal.ZERO;

                BigDecimal optionsExtra = BigDecimal.ZERO;
                if (item.getSelectedOptions() != null) {
                    for (CartItemOption cio : item.getSelectedOptions()) {
                        if (cio.getProductOption() != null && cio.getProductOption().getExtraPrice() != null) {
                            optionsExtra = optionsExtra.add(cio.getProductOption().getExtraPrice());
                        }
                    }
                }

                BigDecimal unitPrice = basePrice.add(optionsExtra);
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
                subtotal = subtotal.add(lineTotal);
            }
        }
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    public PriceBreakdown calculate(Cart cart, Promotion promotion, BigDecimal shippingFee) {
        if (shippingFee == null) {
            shippingFee = DEFAULT_SHIPPING_FEE;
        }
        shippingFee = shippingFee.setScale(2, RoundingMode.HALF_UP);

        // 1. Tính subtotal từ các món trong giỏ
        BigDecimal subtotal = calculateSubtotal(cart);

        // 2. Tính discount từ promotion (nếu có)
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (promotion != null) {
            validatePromotion(promotion, subtotal);
            discountAmount = computeDiscount(promotion, subtotal, shippingFee);
        }
        discountAmount = discountAmount.setScale(2, RoundingMode.HALF_UP);

        // 3. Tính total = subtotal + shippingFee - discountAmount
        BigDecimal total = subtotal.add(shippingFee).subtract(discountAmount);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        total = total.setScale(2, RoundingMode.HALF_UP);

        return PriceBreakdown.builder()
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(discountAmount)
                .total(total)
                .build();
    }

    /**
     * Đơn giá 1 món = giá gốc + tổng phụ phí topping (null-safe toàn bộ).
     * NGUỒN SỰ THẬT DUY NHẤT của công thức — Cart / Order snapshot / checkout dùng chung.
     */
    public static BigDecimal unitPriceOf(CartItem item) {
        BigDecimal basePrice = item.getProduct() != null && item.getProduct().getPrice() != null
                ? item.getProduct().getPrice()
                : BigDecimal.ZERO;

        BigDecimal optionsExtra = BigDecimal.ZERO;
        if (item.getSelectedOptions() != null) {
            for (CartItemOption cio : item.getSelectedOptions()) {
                if (cio.getProductOption() != null && cio.getProductOption().getExtraPrice() != null) {
                    optionsExtra = optionsExtra.add(cio.getProductOption().getExtraPrice());
                }
            }
        }
        return basePrice.add(optionsExtra);
    }

    /** Tổng tiền 1 dòng = unitPrice × quantity (quantity null → 1). */
    public static BigDecimal lineTotalOf(CartItem item) {
        int qty = item.getQuantity() != null ? item.getQuantity() : 1;
        return unitPriceOf(item).multiply(BigDecimal.valueOf(qty));
    }

    private void validatePromotion(Promotion promotion, BigDecimal subtotal) {
        if (!promotion.isActive()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi hiện không kích hoạt");
        }

        LocalDateTime now = LocalDateTime.now();
        if (promotion.getStartsAt() != null && now.isBefore(promotion.getStartsAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi chưa đến thời gian áp dụng");
        }
        if (promotion.getEndsAt() != null && now.isAfter(promotion.getEndsAt())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi đã hết hạn sử dụng");
        }

        if (promotion.getMaxUsage() != null && promotion.getMaxUsage() > 0) {
            int used = promotion.getUsedCount() != null ? promotion.getUsedCount() : 0;
            if (used >= promotion.getMaxUsage()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi đã hết lượt sử dụng");
            }
        }

        if (promotion.getMinOrderAmount() != null && subtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    String.format("Đơn hàng chưa đạt giá trị tối thiểu %,.0fđ để áp dụng mã giảm giá",
                            promotion.getMinOrderAmount().doubleValue()));
        }
    }

    /**
     * Công thức giảm giá duy nhất của hệ thống — cả lúc tạo đơn lẫn lúc kiểm tra mã
     * ({@code POST /promotions/validate}) đều phải gọi hàm này, nếu không khách sẽ thấy
     * số tiền giảm khác với số tiền thực bị trừ.
     */
    public BigDecimal computeDiscount(Promotion promotion, BigDecimal subtotal, BigDecimal shippingFee) {
        DiscountType type = promotion.getDiscountType();
        BigDecimal value = promotion.getValue() != null ? promotion.getValue() : BigDecimal.ZERO;

        if (type == null) {
            return BigDecimal.ZERO;
        }

        return switch (type) {
            case PERCENTAGE -> {
                BigDecimal discount = subtotal.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                    yield promotion.getMaxDiscountAmount();
                }
                yield discount;
            }
            case FIXED_AMOUNT -> value.min(subtotal);
            case FREE_SHIP -> value.min(shippingFee);
        };
    }
}
