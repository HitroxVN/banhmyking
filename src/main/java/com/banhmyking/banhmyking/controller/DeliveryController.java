package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.dto.store.DeliveryQuoteResponse;
import com.banhmyking.banhmyking.dto.store.StoreQuoteOption;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.DeliveryFeeCalculator;
import com.banhmyking.banhmyking.service.ProductPricing;
import com.banhmyking.banhmyking.service.StoreSelectionService;
import com.banhmyking.banhmyking.service.StoreSelectionService.Candidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery & Shipping", description = "Báo giá giao hàng theo cơ sở")
public class DeliveryController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final StoreSelectionService storeSelectionService;
    private final DeliveryFeeCalculator deliveryFeeCalculator;
    private final CartRepository cartRepository;
    private final ProductPricing productPricing;

    @GetMapping("/quote")
    @Transactional(readOnly = true)
    @Operation(summary = "Báo giá giao hàng: cơ sở đề xuất + phí và lý do của từng cơ sở (đọc giỏ hàng phía server)")
    public ResponseEntity<ApiResponse<DeliveryQuoteResponse>> quote(
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude,
            @RequestParam(required = false) String shippingAddress,
            @AuthenticationPrincipal UserDetails principal) {
        Long userId = SecurityUtils.requireUserId(principal);
        Map<Product, Integer> items = new LinkedHashMap<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        Cart cart = cartRepository.findByUserIdWithDetails(userId).orElse(null);
        LocalDateTime pricedAt = productPricing.now();
        if (cart != null && cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                items.merge(item.getProduct(), item.getQuantity(), Integer::sum);
                subtotal = subtotal.add(productPricing.lineTotal(item, pricedAt));
            }
        }
        List<Candidate> candidates = storeSelectionService.evaluate(latitude, longitude, subtotal, items);
        Long recommended = storeSelectionService.recommend(candidates).map(c -> c.store().getId()).orElse(null);
        BigDecimal cartSubtotal = subtotal;
        List<StoreQuoteOption> options = candidates.stream()
                .map(c -> toOption(c, shippingAddress, cartSubtotal)).toList();
        return ResponseEntity.ok(ApiResponse.ok("Báo giá giao hàng thành công",
                new DeliveryQuoteResponse(recommended, options)));
    }

    private StoreQuoteOption toOption(Candidate c, String address, BigDecimal subtotal) {
        DeliveryFeeResult fee = deliveryFeeCalculator.calculateFee(
                c.distanceKm(), address, subtotal, c.store().getFreeShipRadiusKm());
        return StoreQuoteOption.builder()
                .storeId(c.store().getId()).storeCode(c.store().getCode()).storeName(c.store().getName())
                .storeAddress(c.store().getAddress()).storePhone(c.store().getPhone())
                .openTime(c.store().getOpenTime().format(HH_MM)).closeTime(c.store().getCloseTime().format(HH_MM))
                .minOrderAmount(c.store().getMinOrderAmount())
                .distanceKm(c.distanceKm())
                .shippingFee(fee.getShippingFee()).originalFee(fee.getOriginalFee())
                .freeship(fee.isFreeship()).feeDescription(fee.getDescription())
                .eligible(c.eligible())
                .reasons(c.reasons().stream().map(Enum::name).toList())
                .reasonMessages(c.reasons().stream().map(r -> storeSelectionService.describe(r, c)).toList())
                .unavailableItems(c.unavailableItems())
                .build();
    }
}
