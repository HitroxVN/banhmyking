package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.cart.AddToCartRequest;
import com.banhmyking.banhmyking.dto.cart.CartResponse;
import com.banhmyking.banhmyking.dto.cart.UpdateCartItemRequest;

public interface CartService {

    /**
     * Lấy thông tin giỏ hàng của user.
     * Không tạo row rác nếu user chưa từng thêm món.
     */
    CartResponse getCart(Long userId);

    /**
     * Thêm món vào giỏ hàng.
     * Lazy tạo giỏ nếu chưa có.
     * Cộng dồn số lượng nếu trùng toàn bộ tùy chọn (options).
     * Chặn nếu món không khả dụng (isAvailable = false).
     */
    CartResponse addToCart(Long userId, AddToCartRequest request);

    /**
     * Cập nhật số lượng của một dòng món trong giỏ.
     */
    CartResponse updateItemQuantity(Long userId, Long itemId, UpdateCartItemRequest request);

    /**
     * Xóa một dòng món khỏi giỏ hàng.
     */
    CartResponse removeItem(Long userId, Long itemId);

    /**
     * Xóa sạch toàn bộ giỏ hàng của user.
     */
    CartResponse clearCart(Long userId);
}
