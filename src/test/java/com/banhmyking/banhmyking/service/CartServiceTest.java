package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.cart.AddToCartRequest;
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
import com.banhmyking.banhmyking.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private User testUser;
    private Product availableProduct;
    private Product unavailableProduct;
    private ProductOption optionPate;
    private ProductOption optionChaLua;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("customer@banhmyking.vn");
        testUser.setFullName("Khách Hàng Test");

        availableProduct = new Product();
        availableProduct.setId(10L);
        availableProduct.setName("Bánh mì Thập Cẩm");
        availableProduct.setPrice(BigDecimal.valueOf(30000));
        availableProduct.setAvailable(true);
        availableProduct.setDeleted(false);

        unavailableProduct = new Product();
        unavailableProduct.setId(20L);
        unavailableProduct.setName("Bánh mì Chảo");
        unavailableProduct.setPrice(BigDecimal.valueOf(45000));
        unavailableProduct.setAvailable(false);
        unavailableProduct.setDeleted(false);

        optionPate = new ProductOption();
        optionPate.setId(101L);
        optionPate.setProduct(availableProduct);
        optionPate.setName("Thêm pate");
        optionPate.setExtraPrice(BigDecimal.valueOf(5000));

        optionChaLua = new ProductOption();
        optionChaLua.setId(102L);
        optionChaLua.setProduct(availableProduct);
        optionChaLua.setName("Thêm chả lụa");
        optionChaLua.setExtraPrice(BigDecimal.valueOf(8000));
    }

    @Test
    @DisplayName("AC 1: getCart khi chưa có giỏ hàng trả về giỏ rỗng và không tạo row rác trong DB")
    void getCart_whenCartDoesNotExist_shouldReturnEmptyResponseWithoutCreatingRow() {
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.empty());

        CartResponse response = cartService.getCart(1L);

        assertThat(response).isNotNull();
        assertThat(response.getCartId()).isNull();
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalQuantity()).isEqualTo(0);
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);

        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC 1: addToCart khi chưa có giỏ hàng sẽ tự động tạo giỏ hàng mới (Lazy init)")
    void addToCart_whenCartDoesNotExist_shouldLazyInitCart() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(availableProduct));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart c = invocation.getArgument(0);
            c.setId(100L);
            return c;
        });

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(10L)
                .quantity(1)
                .build();

        CartResponse response = cartService.addToCart(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getCartId()).isEqualTo(100L);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getTotalQuantity()).isEqualTo(1);
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(30000));
    }

    @Test
    @DisplayName("AC 5: addToCart chặn thêm món nếu món ăn có trạng thái isAvailable = false")
    void addToCart_whenProductIsUnavailable_shouldThrowBusinessException() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(productRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(unavailableProduct));

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(20L)
                .quantity(1)
                .build();

        assertThatThrownBy(() -> cartService.addToCart(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hiện không khả dụng")
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BUSINESS_ERROR);

        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("Chặn khi tùy chọn không thuộc về món ăn")
    void addToCart_whenOptionDoesNotBelongToProduct_shouldThrowBusinessException() {
        Product anotherProduct = new Product();
        anotherProduct.setId(99L);
        anotherProduct.setName("Món khác");

        ProductOption invalidOption = new ProductOption();
        invalidOption.setId(999L);
        invalidOption.setProduct(anotherProduct);
        invalidOption.setName("Topping của món khác");
        invalidOption.setExtraPrice(BigDecimal.valueOf(5000));

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(availableProduct));
        when(productOptionRepository.findAllById(List.of(999L))).thenReturn(List.of(invalidOption));

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(10L)
                .quantity(1)
                .optionIds(List.of(999L))
                .build();

        assertThatThrownBy(() -> cartService.addToCart(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("không thuộc về món ăn");

        verify(cartRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC 2: addToCart trùng hoàn toàn tùy chọn thì tự động cộng dồn số lượng")
    void addToCart_whenMatchingExactSameOptions_shouldAggregateQuantity() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUser(testUser);

        CartItem existingItem = new CartItem();
        existingItem.setId(1001L);
        existingItem.setCart(cart);
        existingItem.setProduct(availableProduct);
        existingItem.setQuantity(2);

        CartItemOption cio1 = new CartItemOption();
        cio1.setCartItem(existingItem);
        cio1.setProductOption(optionPate);

        CartItemOption cio2 = new CartItemOption();
        cio2.setCartItem(existingItem);
        cio2.setProductOption(optionChaLua);

        existingItem.getSelectedOptions().add(cio1);
        existingItem.getSelectedOptions().add(cio2);
        cart.getItems().add(existingItem);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(availableProduct));
        when(productOptionRepository.findAllById(any())).thenReturn(List.of(optionChaLua, optionPate));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        // Gửi danh sách options thứ tự ngược lại: [102L, 101L] (vẫn trùng hoàn toàn)
        AddToCartRequest request = AddToCartRequest.builder()
                .productId(10L)
                .quantity(3)
                .optionIds(List.of(102L, 101L))
                .build();

        CartResponse response = cartService.addToCart(1L, request);

        assertThat(response.getItems()).hasSize(1);
        // Số lượng được cộng dồn: 2 + 3 = 5
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(response.getTotalQuantity()).isEqualTo(5);
        // Đơn giá: 30,000 + 5,000 + 8,000 = 43,000 -> 43,000 x 5 = 215,000
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(215000));
    }

    @Test
    @DisplayName("AC 2: addToCart cùng món nhưng khác tùy chọn thì tạo dòng mới")
    void addToCart_whenSameProductDifferentOptions_shouldCreateNewCartItem() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUser(testUser);

        CartItem existingItem = new CartItem();
        existingItem.setId(1001L);
        existingItem.setCart(cart);
        existingItem.setProduct(availableProduct);
        existingItem.setQuantity(1);

        CartItemOption cio1 = new CartItemOption();
        cio1.setCartItem(existingItem);
        cio1.setProductOption(optionPate);
        existingItem.getSelectedOptions().add(cio1);
        cart.getItems().add(existingItem);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(productRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(availableProduct));
        when(productOptionRepository.findAllById(List.of(102L))).thenReturn(List.of(optionChaLua));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        AddToCartRequest request = AddToCartRequest.builder()
                .productId(10L)
                .quantity(2)
                .optionIds(List.of(102L)) // Chỉ chọn chả lụa, khác dòng trước (chọn pate)
                .build();

        CartResponse response = cartService.addToCart(1L, request);

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("AC 3 & AC 4: updateItemQuantity sửa số lượng và tự động tính lại subtotal")
    void updateItemQuantity_shouldUpdateQuantityAndRecalculate() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUser(testUser);

        CartItem item = new CartItem();
        item.setId(500L);
        item.setCart(cart);
        item.setProduct(availableProduct); // 30,000
        item.setQuantity(1);
        cart.getItems().add(item);

        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        UpdateCartItemRequest updateRequest = UpdateCartItemRequest.builder()
                .quantity(4)
                .build();

        CartResponse response = cartService.updateItemQuantity(1L, 500L, updateRequest);

        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(4);
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(120000));
    }

    @Test
    @DisplayName("AC 3: removeItem xóa 1 dòng item khỏi giỏ hàng")
    void removeItem_shouldRemoveItemFromCart() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUser(testUser);

        CartItem item1 = new CartItem();
        item1.setId(501L);
        item1.setCart(cart);
        item1.setProduct(availableProduct);
        item1.setQuantity(1);

        CartItem item2 = new CartItem();
        item2.setId(502L);
        item2.setCart(cart);
        item2.setProduct(availableProduct);
        item2.setQuantity(2);

        cart.getItems().add(item1);
        cart.getItems().add(item2);

        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        CartResponse response = cartService.removeItem(1L, 501L);

        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo(502L);
        assertThat(response.getTotalQuantity()).isEqualTo(2);
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(60000));
    }

    @Test
    @DisplayName("AC 3: clearCart xóa sạch giỏ hàng của user")
    void clearCart_shouldDeleteCart() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUser(testUser);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CartResponse response = cartService.clearCart(1L);

        verify(cartRepository).delete(cart);
        assertThat(response.getCartId()).isNull();
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalQuantity()).isEqualTo(0);
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("AC 4: Tạm tính (subtotal) tính toán chính xác giá gốc + options * quantity")
    void subtotalCalculation_shouldCalculateCorrectlyOnServer() {
        Cart cart = new Cart();
        cart.setId(100L);
        cart.setUser(testUser);

        CartItem item = new CartItem();
        item.setId(500L);
        item.setCart(cart);
        item.setProduct(availableProduct); // 30,000
        item.setQuantity(3); // 3 phần

        CartItemOption opt = new CartItemOption();
        opt.setCartItem(item);
        opt.setProductOption(optionPate); // +5,000
        item.getSelectedOptions().add(opt);

        cart.getItems().add(item);

        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(cart));

        CartResponse response = cartService.getCart(1L);

        // Đơn giá: 30,000 + 5,000 = 35,000
        // Tạm tính: 35,000 * 3 = 105,000
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(35000));
        assertThat(response.getItems().get(0).getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(105000));
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(105000));
    }
}
