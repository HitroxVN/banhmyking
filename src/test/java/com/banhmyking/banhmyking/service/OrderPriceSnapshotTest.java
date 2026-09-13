package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.dto.order.CreateOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.PriceBreakdown;
import com.banhmyking.banhmyking.entity.Address;
import com.banhmyking.banhmyking.entity.Cart;
import com.banhmyking.banhmyking.entity.CartItem;
import com.banhmyking.banhmyking.entity.CartItemOption;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.OrderItemOption;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.ProductOption;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.PaymentMethod;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.CartRepository;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.OrderStatusHistoryRepository;
import com.banhmyking.banhmyking.repository.PaymentRepository;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.OrderServiceImpl;
import com.banhmyking.banhmyking.util.OrderCodeGenerator;
import com.banhmyking.banhmyking.validator.OrderStatusValidator;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPriceSnapshotTest {

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
    private DeliveryFeeCalculator deliveryFeeCalculator;

    @Mock
    private PaymentService paymentService;

    @Mock
    private OrderCodeGenerator orderCodeGenerator;

    @Mock
    private OrderStatusValidator orderStatusValidator;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private User customer;
    private Product product;
    private ProductOption option;
    private Cart cart;
    private Address address;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L);
        customer.setFullName("Khách Hàng Test");
        customer.setRole(RoleName.CUSTOMER);

        product = new Product();
        product.setId(10L);
        product.setName("Bánh mì Chả lụa");
        product.setPrice(BigDecimal.valueOf(30000));
        product.setAvailable(true);
        product.setDeleted(false);

        option = new ProductOption();
        option.setId(101L);
        option.setName("Thêm pate");
        option.setExtraPrice(BigDecimal.valueOf(5000));

        cart = new Cart();
        cart.setId(100L);
        cart.setUser(customer);

        CartItem cartItem = new CartItem();
        cartItem.setId(50L);
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2); // 2 ổ bánh mì

        CartItemOption cartItemOption = new CartItemOption();
        cartItemOption.setId(501L);
        cartItemOption.setCartItem(cartItem);
        cartItemOption.setProductOption(option);

        cartItem.getSelectedOptions().add(cartItemOption);
        cart.getItems().add(cartItem);

        address = new Address();
        address.setId(200L);
        address.setUser(customer);
        address.setReceiverName("Khách Hàng Test");
        address.setReceiverPhone("0901234567");
        address.setFullAddress("123 Lê Lợi, Quận 1, TP.HCM");
    }

    @Test
    @DisplayName("AC 1: Snapshot giá - Khi Product và Option tăng giá sau khi chốt đơn, Order cũ vẫn giữ nguyên giá trị")
    void orderPriceSnapshot_whenProductPriceChanges_orderTotalsRemainUnchanged() {
        // GIVEN: Đơn hàng ban đầu được tính: Subtotal = (30.000 + 5.000) * 2 = 70.000đ, Ship = 15.000đ -> Total = 85.000đ
        CreateOrderRequest request = CreateOrderRequest.builder()
                .addressId(200L)
                .distanceKm(BigDecimal.valueOf(1.5))
                .paymentMethod(PaymentMethod.COD)
                .note("Không hành")
                .build();

        DeliveryFeeResult deliveryResult = DeliveryFeeResult.builder()
                .shippingFee(BigDecimal.valueOf(15000))
                .originalFee(BigDecimal.valueOf(15000))
                .freeship(false)
                .build();

        PriceBreakdown breakdown = PriceBreakdown.builder()
                .subtotal(BigDecimal.valueOf(70000))
                .shippingFee(BigDecimal.valueOf(15000))
                .discountAmount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(85000))
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(cartRepository.findByUserIdWithDetails(1L)).thenReturn(Optional.of(cart));
        when(addressRepository.findByIdAndUserId(200L, 1L)).thenReturn(Optional.of(address));
        when(deliveryFeeCalculator.calculateFee(any(), any(), any())).thenReturn(deliveryResult);
        when(priceCalculator.calculate(eq(cart), any(), eq(BigDecimal.valueOf(15000)))).thenReturn(breakdown);
        when(orderCodeGenerator.generateUniqueCode(any(), anyInt())).thenReturn("BMK-20260912-SNAP1");

        // Mô phỏng OrderRepository.save lưu và giữ bản ghi Order
        final List<Order> savedOrders = new ArrayList<>();
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(999L);
            }
            savedOrders.add(o);
            return o;
        });

        // WHEN: Chốt đơn từ giỏ hàng
        OrderResponse initialOrderResponse = orderService.createFromCart(1L, request);

        // THEN: Kiểm tra snapshot tại thời điểm tạo đơn
        assertThat(initialOrderResponse).isNotNull();
        assertThat(initialOrderResponse.getOrderCode()).isEqualTo("BMK-20260912-SNAP1");
        assertThat(initialOrderResponse.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(70000));
        assertThat(initialOrderResponse.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(initialOrderResponse.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(85000));
        assertThat(initialOrderResponse.getItems()).hasSize(1);

        var itemResponse = initialOrderResponse.getItems().get(0);
        assertThat(itemResponse.getProductName()).isEqualTo("Bánh mì Chả lụa");
        assertThat(itemResponse.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(itemResponse.getQuantity()).isEqualTo(2);
        assertThat(itemResponse.getOptions()).hasSize(1);
        assertThat(itemResponse.getOptions().get(0).getOptionName()).isEqualTo("Thêm pate");
        assertThat(itemResponse.getOptions().get(0).getOptionPrice()).isEqualByComparingTo(BigDecimal.valueOf(5000));

        // Lấy thực thể Order đã được lưu trong DB
        Order savedOrder = savedOrders.get(0);

        // THAY ĐỔI DỮ LIỆU PRODUCT VÀ OPTION TRONG DATABASE (Giá tăng gấp đôi, đổi tên, bị xóa)
        product.setPrice(BigDecimal.valueOf(60000)); // Giá tăng từ 30k -> 60k
        product.setName("Bánh mì Chả lụa Premium Hảo Hạng");
        product.setDeleted(true); // Món bị ngừng bán

        option.setExtraPrice(BigDecimal.valueOf(15000)); // Topping tăng từ 5k -> 15k
        option.setName("Topping Pate Gan Ngỗng");

        // KHI TRUY VẤN LẠI ĐƠN HÀNG ĐÃ TẠO (getOrderByCode)
        when(orderRepository.findByOrderCodeWithDetails("BMK-20260912-SNAP1")).thenReturn(Optional.of(savedOrder));
        OrderResponse retrievedOrderResponse = orderService.getOrderByCode(1L, "BMK-20260912-SNAP1");

        // XÁC NHẬN: Mọi thông số tiền tệ, tên món, topping của đơn hàng cũ HOÀN TOÀN KHÔNG BỊ ẢNH HƯỞNG!
        assertThat(retrievedOrderResponse.getSubtotal()).isEqualByComparingTo(BigDecimal.valueOf(70000));
        assertThat(retrievedOrderResponse.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(retrievedOrderResponse.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(85000));

        var retrievedItem = retrievedOrderResponse.getItems().get(0);
        assertThat(retrievedItem.getProductName()).isEqualTo("Bánh mì Chả lụa");
        assertThat(retrievedItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(retrievedItem.getOptions().get(0).getOptionName()).isEqualTo("Thêm pate");
        assertThat(retrievedItem.getOptions().get(0).getOptionPrice()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    @DisplayName("AC 1: Snapshot giá - OrderItem và OrderItemOption độc lập hoàn toàn với Product Entity")
    void orderItemEntities_areIndependentFromProductEntities() {
        // Tạo thủ công thực thể Order và OrderItem với snapshot
        Order order = new Order();
        order.setId(101L);
        order.setOrderCode("BMK-20260912-TEST2");
        order.setSubtotal(BigDecimal.valueOf(35000));
        order.setShippingFee(BigDecimal.valueOf(15000));
        order.setTotal(BigDecimal.valueOf(50000));

        OrderItem orderItem = new OrderItem();
        orderItem.setId(201L);
        orderItem.setOrder(order);
        orderItem.setProduct(product); // Tham chiếu tới Product id 10
        orderItem.setProductName("Bánh mì Chả lụa");
        orderItem.setUnitPrice(BigDecimal.valueOf(30000)); // Giá snapshot
        orderItem.setQuantity(1);
        orderItem.setLineTotal(BigDecimal.valueOf(35000));

        OrderItemOption itemOption = new OrderItemOption();
        itemOption.setId(301L);
        itemOption.setOrderItem(orderItem);
        itemOption.setOptionName("Thêm pate");
        itemOption.setOptionPrice(BigDecimal.valueOf(5000)); // Giá topping snapshot

        orderItem.getOptions().add(itemOption);
        order.getItems().add(orderItem);

        // Sau đó Product và Option bị thay đổi giá
        product.setPrice(BigDecimal.valueOf(999999));
        option.setExtraPrice(BigDecimal.valueOf(888888));

        // Kiểm tra snapshot của OrderItem vẫn là giá cũ
        assertThat(orderItem.getUnitPrice()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(orderItem.getProductName()).isEqualTo("Bánh mì Chả lụa");
        assertThat(orderItem.getOptions().get(0).getOptionPrice()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(orderItem.getOptions().get(0).getOptionName()).isEqualTo("Thêm pate");
        assertThat(order.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }
}
