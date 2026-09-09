package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.cart.AddToCartRequest;
import com.banhmyking.banhmyking.dto.cart.CartResponse;
import com.banhmyking.banhmyking.dto.cart.UpdateCartItemRequest;
import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.service.CartService;
import com.banhmyking.banhmyking.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "APIs quản lý giỏ hàng")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Xem giỏ hàng", description = "Lấy thông tin giỏ hàng và tạm tính subtotal. Không tạo bản ghi rác nếu user chưa thêm món.")
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId) {
        Long userId = SecurityUtils.resolveUserId(headerUserId);
        CartResponse cartResponse = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy giỏ hàng thành công", cartResponse));
    }

    @PostMapping("/items")
    @Operation(summary = "Thêm món vào giỏ hàng", description = "Thêm món kèm các tùy chọn. Tự động lazy init giỏ hàng và cộng dồn số lượng nếu trùng hoàn toàn tùy chọn. Chặn nếu món không khả dụng.")
    public ResponseEntity<ApiResponse<CartResponse>> addToCart(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @Valid @RequestBody AddToCartRequest request) {
        Long userId = SecurityUtils.resolveUserId(headerUserId);
        CartResponse cartResponse = cartService.addToCart(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Thêm món vào giỏ hàng thành công", cartResponse));
    }

    @PutMapping("/items/{itemId}")
    @Operation(summary = "Cập nhật số lượng món", description = "Sửa số lượng của một dòng món trong giỏ và tự động tính lại subtotal.")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @Parameter(description = "ID dòng món trong giỏ (cart_items.id)", example = "1")
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {
        Long userId = SecurityUtils.resolveUserId(headerUserId);
        CartResponse cartResponse = cartService.updateItemQuantity(userId, itemId, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật số lượng thành công", cartResponse));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Xóa một món khỏi giỏ", description = "Xóa dòng món tương ứng khỏi giỏ hàng và tự động tính lại subtotal.")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId,
            @Parameter(description = "ID dòng món trong giỏ (cart_items.id)", example = "1")
            @PathVariable Long itemId) {
        Long userId = SecurityUtils.resolveUserId(headerUserId);
        CartResponse cartResponse = cartService.removeItem(userId, itemId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa món khỏi giỏ hàng thành công", cartResponse));
    }

    @DeleteMapping
    @Operation(summary = "Xóa sạch giỏ hàng", description = "Xóa toàn bộ các món trong giỏ hàng của người dùng.")
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(
            @Parameter(description = "ID người dùng (mặc định: 1 khi test Swagger)", example = "1")
            @RequestHeader(value = "X-User-Id", required = false, defaultValue = "1") Long headerUserId) {
        Long userId = SecurityUtils.resolveUserId(headerUserId);
        CartResponse cartResponse = cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.ok("Xóa sạch giỏ hàng thành công", cartResponse));
    }
}
