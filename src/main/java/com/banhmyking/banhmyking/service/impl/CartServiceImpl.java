package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.cart.AddToCartRequest;
import com.banhmyking.banhmyking.dto.cart.CartItemOptionResponse;
import com.banhmyking.banhmyking.dto.cart.CartItemResponse;
import com.banhmyking.banhmyking.dto.cart.CartResponse;
import com.banhmyking.banhmyking.dto.cart.UpdateCartItemRequest;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.CartItemRepository;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.repository.ProductOptionRepository;
import com.banhmyking.banhmyking.repository.ProductRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long userId) {
        log.debug("Fetching cart for user {}", userId);
        // AC 1: Không tạo row rác nếu user chưa đặt món
        return cartRepository.findByUserIdWithDetails(userId)
                .map(this::toCartResponse)
                .orElseGet(CartResponse::empty);
    }

    @Override
    public CartResponse addToCart(Long userId, AddToCartRequest request) {
        log.info("Adding item to cart for user {}: productId={}, quantity={}",
                userId, request.getProductId(), request.getQuantity());

        // 1. Kiểm tra User
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        // 2. Kiểm tra Product & AC 5 (isAvailable = false)
        Product product = productRepository.findByIdAndDeletedFalse(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món ăn với ID: " + request.getProductId()));

        if (!product.isAvailable()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Món ăn '" + product.getName() + "' hiện không khả dụng (hết hàng hoặc tạm ngưng bán)");
        }

        // 3. Kiểm tra các tùy chọn (ProductOption)
        List<Long> cleanOptionIds = request.getOptionIds() != null
                ? request.getOptionIds().stream().filter(Objects::nonNull).distinct().toList()
                : List.of();

        List<ProductOption> options = new ArrayList<>();
        if (!cleanOptionIds.isEmpty()) {
            options = productOptionRepository.findAllById(cleanOptionIds);
            if (options.size() != cleanOptionIds.size()) {
                throw new ResourceNotFoundException("Một hoặc nhiều tùy chọn không tồn tại");
            }
            for (ProductOption opt : options) {
                if (!opt.getProduct().getId().equals(product.getId())) {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                            "Tùy chọn '" + opt.getName() + "' không thuộc về món ăn '" + product.getName() + "'");
                }
            }
        }

        // 4. AC 1: Lazy init giỏ hàng nếu chưa có
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> {
            log.info("Lazy initializing cart for user {}", userId);
            Cart newCart = new Cart();
            newCart.setUser(user);
            return cartRepository.save(newCart);
        });

        // 5. AC 2: Kiểm tra trùng lặp toàn bộ options để cộng dồn số lượng
        Set<Long> targetOptionIds = new HashSet<>(cleanOptionIds);
        CartItem matchingItem = null;

        for (CartItem existingItem : cart.getItems()) {
            if (existingItem.getProduct().getId().equals(product.getId())) {
                Set<Long> existingOptionIds = existingItem.getSelectedOptions().stream()
                        .map(cio -> cio.getProductOption().getId())
                        .collect(Collectors.toSet());

                if (existingOptionIds.equals(targetOptionIds)) {
                    matchingItem = existingItem;
                    break;
                }
            }
        }

        if (matchingItem != null) {
            log.info("Item with matching options already exists in cart. Incrementing quantity from {} by {}",
                    matchingItem.getQuantity(), request.getQuantity());
            matchingItem.setQuantity(matchingItem.getQuantity() + request.getQuantity());
        } else {
            log.info("Creating new CartItem in cart for product {}", product.getId());
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setProduct(product);
            newItem.setQuantity(request.getQuantity());

            for (ProductOption opt : options) {
                CartItemOption itemOpt = new CartItemOption();
                itemOpt.setCartItem(newItem);
                itemOpt.setProductOption(opt);
                newItem.getSelectedOptions().add(itemOpt);
            }

            cart.getItems().add(newItem);
        }

        cart = cartRepository.save(cart);
        // AC 4: Tạm tính hoàn toàn ở server
        return toCartResponse(cart);
    }

    @Override
    public CartResponse updateItemQuantity(Long userId, Long itemId, UpdateCartItemRequest request) {
        log.info("Updating quantity of item {} to {} for user {}", itemId, request.getQuantity(), userId);

        Cart cart = cartRepository.findByUserIdWithDetails(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Giỏ hàng của người dùng không tồn tại"));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món có ID " + itemId + " trong giỏ hàng"));

        item.setQuantity(request.getQuantity());
        cart = cartRepository.save(cart);

        return toCartResponse(cart);
    }

    @Override
    public CartResponse removeItem(Long userId, Long itemId) {
        log.info("Removing item {} from cart for user {}", itemId, userId);

        Cart cart = cartRepository.findByUserIdWithDetails(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Giỏ hàng của người dùng không tồn tại"));

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy món có ID " + itemId + " trong giỏ hàng"));

        cart.getItems().remove(item);
        cart = cartRepository.save(cart);

        return toCartResponse(cart);
    }

    @Override
    public CartResponse clearCart(Long userId) {
        log.info("Clearing cart for user {}", userId);

        Optional<Cart> cartOpt = cartRepository.findByUserId(userId);
        if (cartOpt.isPresent()) {
            cartRepository.delete(cartOpt.get());
        }

        return CartResponse.empty();
    }

    /**
     * AC 4: Tạm tính (subtotal) được tính toán hoàn toàn ở server.
     * unitPrice = basePrice + sum(extraPrice)
     * itemSubtotal = unitPrice * quantity
     * subtotal = sum(itemSubtotal)
     */
    private CartResponse toCartResponse(Cart cart) {
        if (cart == null) {
            return CartResponse.empty();
        }

        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal totalSubtotal = BigDecimal.ZERO;
        int totalQuantity = 0;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                Product product = item.getProduct();
                BigDecimal basePrice = (product != null && product.getPrice() != null)
                        ? product.getPrice()
                        : BigDecimal.ZERO;

                BigDecimal optionsExtraPrice = BigDecimal.ZERO;
                List<CartItemOptionResponse> optionResponses = new ArrayList<>();

                if (item.getSelectedOptions() != null) {
                    for (CartItemOption cio : item.getSelectedOptions()) {
                        ProductOption po = cio.getProductOption();
                        BigDecimal extra = (po != null && po.getExtraPrice() != null)
                                ? po.getExtraPrice()
                                : BigDecimal.ZERO;
                        optionsExtraPrice = optionsExtraPrice.add(extra);

                        optionResponses.add(CartItemOptionResponse.builder()
                                .id(cio.getId())
                                .productOptionId(po != null ? po.getId() : null)
                                .name(po != null ? po.getName() : null)
                                .extraPrice(extra)
                                .build());
                    }
                }

                BigDecimal unitPrice = basePrice.add(optionsExtraPrice);
                int qty = item.getQuantity() != null ? item.getQuantity() : 1;
                BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(qty));

                totalQuantity += qty;
                totalSubtotal = totalSubtotal.add(itemSubtotal);

                itemResponses.add(CartItemResponse.builder()
                        .id(item.getId())
                        .productId(product != null ? product.getId() : null)
                        .productName(product != null ? product.getName() : null)
                        .productImageUrl(product != null ? product.getImageUrl() : null)
                        .basePrice(basePrice)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .subtotal(itemSubtotal)
                        .options(optionResponses)
                        .build());
            }
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .totalQuantity(totalQuantity)
                .subtotal(totalSubtotal)
                .build();
    }
}
