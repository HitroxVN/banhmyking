package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.order.CreateOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.PriceBreakdown;
import com.banhmyking.banhmyking.entity.Address;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Payment;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.OrderServiceImpl;
import com.banhmyking.banhmyking.util.OrderCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartService cartService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private PromotionUsageRepository promotionUsageRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PriceCalculator priceCalculator;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User testUser;
    private Cart testCart;
    private Product testProduct;
    private ProductOption testOption;
    private Address testAddress;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("customer@banhmyking.vn");
        testUser.setFullName("Khách Hàng Test");

        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setName("Bánh mì Đặc Biệt");
        testProduct.setPrice(BigDecimal.valueOf(35000));
        testProduct.setAvailable(true);
        testProduct.setDeleted(false);

        testOption = new ProductOption();
        testOption.setId(101L);
        testOption.setName("Thêm pate");
        testOption.setExtraPrice(BigDecimal.valueOf(5000));

        testCart = new Cart();
        testCart.setId(100L);
        testCart.setUser(testUser);

        CartItem item = new CartItem();
        item.setId(500L);
        item.setCart(testCart);
        item.setProduct(testProduct);
        item.setQuantity(2);

        CartItemOption itemOpt = new CartItemOption();
        itemOpt.setCartItem(item);
        itemOpt.setProductOption(testOption);
        item.getSelectedOptions().add(itemOpt);

        testCart.getItems().add(item);

        testAddress = new Address();
        testAddress.setId(200L);
        testAddress.setUser(testUser);
        testAddress.setReceiverName("Nguyễn Văn A");
        testAddress.setReceiverPhone("0901234567");
        testAddress.setFullAddress("123 Lê Lợi, Q1, TP.HCM");
    }

    @Test
    @DisplayName("AC 2, 4, 5, 6: Tạo đơn hàng thành công, snapshot chuẩn xác và xóa giỏ hàng")
    void createFromCart_success() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(200L)
                .paymentMethod(PaymentMethod.COD)
                .note("Không ớt")
                .build();

        PriceBreakdown breakdown = PriceBreakdown.builder()
                .subtotal(BigDecimal.valueOf(80000))
                .shippingFee(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(95000))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));
        when(addressRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(testAddress));
        when(priceCalculator.calculate(eq(testCart), any())).thenReturn(breakdown);
        when(orderCodeGenerator.generateUniqueCode(any(), anyInt())).thenReturn("BMK-20260908-ABC12");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        OrderResponse response = orderService.createFromCart(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getOrderCode()).isEqualTo("BMK-20260908-ABC12");
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getReceiverName()).isEqualTo("Nguyễn Văn A");
        assertThat(response.getReceiverPhone()).isEqualTo("0901234567");
        assertThat(response.getShippingAddress()).isEqualTo("123 Lê Lợi, Q1, TP.HCM");

        // Kiểm tra tiền tệ snapshot
        assertThat(response.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(80000));
        assertThat(response.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(response.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(95000));

        // Kiểm tra snapshot items
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getProductName()).isEqualTo("Bánh mì Đặc Biệt");
        assertThat(response.getItems().get(0).getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(35000));
        assertThat(response.getItems().get(0).getQuantity()).isEqualTo(2);

        // Kiểm tra snapshot options
        assertThat(response.getItems().get(0).getOptions()).hasSize(1);
        assertThat(response.getItems().get(0).getOptions().get(0).getOptionName()).isEqualTo("Thêm pate");
        assertThat(response.getItems().get(0).getOptions().get(0).getOptionPrice()).isEqualByComparingTo(BigDecimal.valueOf(5000));

        // AC 6: Giỏ hàng phải được dọn sạch
        verify(cartService).clearCart(1L);
        // Lưu đơn hàng và payment
        verify(orderRepository).save(any(Order.class));
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    @DisplayName("AC 3: Chặn tạo đơn khi giỏ hàng đang trống")
    void createFromCart_whenCartIsEmpty_shouldThrowBusinessException() {
        testCart.getItems().clear();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(200L)
                .build();

        assertThatThrownBy(() -> orderService.createFromCart(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giỏ hàng đang trống");

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    @Test
    @DisplayName("AC 3: Chặn tạo đơn khi có món ăn trong giỏ đã bị hết hàng (isAvailable = false)")
    void createFromCart_whenProductUnavailable_shouldThrowBusinessException() {
        testProduct.setAvailable(false); // Món hết hàng
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(200L)
                .build();

        assertThatThrownBy(() -> orderService.createFromCart(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("hiện không khả dụng");

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }

    @Test
    @DisplayName("Tạo đơn với thông tin địa chỉ nhập trực tiếp (không dùng addressId)")
    void createFromCart_withManualAddress_success() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .receiverName("Trần Thị B")
                .receiverPhone("0988776655")
                .shippingAddress("456 Nguyễn Huệ, Q1, TP.HCM")
                .build();

        PriceBreakdown breakdown = PriceBreakdown.builder()
                .subtotal(BigDecimal.valueOf(80000))
                .shippingFee(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(95000))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));
        when(priceCalculator.calculate(eq(testCart), any())).thenReturn(breakdown);
        when(orderCodeGenerator.generateUniqueCode(any(), anyInt())).thenReturn("BMK-20260908-XYZ99");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createFromCart(1L, request);

        assertThat(response.getReceiverName()).isEqualTo("Trần Thị B");
        assertThat(response.getReceiverPhone()).isEqualTo("0988776655");
        assertThat(response.getShippingAddress()).isEqualTo("456 Nguyễn Huệ, Q1, TP.HCM");
        verify(cartService).clearCart(1L);
    }

    @Test
    @DisplayName("Chặn áp dụng promotion khi người dùng đã từng sử dụng mã này")
    void createFromCart_whenPromotionAlreadyUsed_shouldThrowBusinessException() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(200L)
                .promotionCode("SALE10")
                .build();

        Promotion promo = new Promotion();
        promo.setId(10L);
        promo.setCode("SALE10");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(testCart));
        when(addressRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(testAddress));
        when(promotionRepository.findByCodeAndActiveTrue("SALE10")).thenReturn(Optional.of(promo));
        when(promotionUsageRepository.findByPromotionIdAndUserId(10L, 1L)).thenReturn(Optional.of(new com.banhmyking.banhmyking.entity.PromotionUsage()));

        assertThatThrownBy(() -> orderService.createFromCart(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn đã sử dụng mã khuyến mãi");

        verify(orderRepository, never()).save(any());
        verify(cartService, never()).clearCart(any());
    }
}
