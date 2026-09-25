package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.review.CreateReviewRequest;
import com.banhmyking.banhmyking.dto.review.ReviewResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Review;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.ReviewRepository;
import com.banhmyking.banhmyking.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private ReviewServiceImpl reviewService;

    private User testUser;
    private User otherUser;
    private Product testProduct;
    private Order testOrder;
    private OrderItem testOrderItem;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setFullName("Nguyễn Văn A");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setFullName("Trần Thị B");

        testProduct = new Product();
        testProduct.setId(10L);
        testProduct.setName("Bánh mì Thịt Nướng");
        testProduct.setPrice(BigDecimal.valueOf(30000));

        testOrder = new Order();
        testOrder.setId(100L);
        testOrder.setOrderCode("BMK-20260914-REV01");
        testOrder.setUser(testUser);
        testOrder.setStatus(OrderStatus.DELIVERED);

        testOrderItem = new OrderItem();
        testOrderItem.setId(500L);
        testOrderItem.setOrder(testOrder);
        testOrderItem.setProduct(testProduct);
        testOrderItem.setProductName("Bánh mì Thịt Nướng");
        testOrderItem.setUnitPrice(BigDecimal.valueOf(30000));
        testOrderItem.setQuantity(1);
    }

    @Test
    @DisplayName("REVIEW-01: Tạo đánh giá món ăn thành công khi đơn hàng đã DELIVERED và đúng ownership")
    void createReview_success() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .orderItemId(500L)
                .rating(5)
                .comment("Bánh mì rất giòn và ngon!")
                .build();

        when(orderItemRepository.findById(500L)).thenReturn(Optional.of(testOrderItem));
        when(reviewRepository.existsByOrderItemId(500L)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(1000L);
            return r;
        });

        ReviewResponse response = reviewService.createReview(1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1000L);
        assertThat(response.getRating()).isEqualTo(5);
        assertThat(response.getComment()).isEqualTo("Bánh mì rất giòn và ngon!");
        assertThat(response.getProductId()).isEqualTo(10L);
        assertThat(response.getProductName()).isEqualTo("Bánh mì Thịt Nướng");

        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    @DisplayName("REVIEW-01: Bắn lỗi FORBIDDEN khi người dùng đánh giá đơn hàng của người khác")
    void createReview_whenNotOwner_shouldThrowForbiddenException() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .orderItemId(500L)
                .rating(4)
                .comment("Hợp vị")
                .build();

        when(orderItemRepository.findById(500L)).thenReturn(Optional.of(testOrderItem));

        assertThatThrownBy(() -> reviewService.createReview(2L, request)) // User 2 không phải chủ đơn
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Bạn không có quyền đánh giá món ăn trong đơn hàng của người khác");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("REVIEW-01: Bắn lỗi BUSINESS_ERROR khi đơn hàng chưa ở trạng thái DELIVERED")
    void createReview_whenOrderNotDelivered_shouldThrowBusinessException() {
        testOrder.setStatus(OrderStatus.DELIVERING); // Đơn đang giao, chưa DELIVERED

        CreateReviewRequest request = CreateReviewRequest.builder()
                .orderItemId(500L)
                .rating(5)
                .build();

        when(orderItemRepository.findById(500L)).thenReturn(Optional.of(testOrderItem));

        assertThatThrownBy(() -> reviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Chỉ có thể đánh giá món ăn khi đơn hàng đã được giao thành công (DELIVERED)");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("REVIEW-01: Bắn lỗi CONFLICT (409) khi order_item đã từng được đánh giá trước đó")
    void createReview_whenAlreadyReviewed_shouldThrowConflictException() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .orderItemId(500L)
                .rating(5)
                .build();

        when(orderItemRepository.findById(500L)).thenReturn(Optional.of(testOrderItem));
        when(reviewRepository.existsByOrderItemId(500L)).thenReturn(true); // Đã đánh giá rồi

        assertThatThrownBy(() -> reviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT)
                .hasMessageContaining("đã được đánh giá trước đó");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("REVIEW-01: Bắn lỗi VALIDATION_ERROR khi điểm rating không nằm trong khoảng 1-5")
    void createReview_whenRatingInvalid_shouldThrowValidationException() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .orderItemId(500L)
                .rating(6) // Rating > 5
                .build();

        assertThatThrownBy(() -> reviewService.createReview(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Điểm đánh giá phải nằm trong khoảng từ 1 đến 5");

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("REVIEW-01: Bắn lỗi ResourceNotFoundException khi orderItemId không tồn tại")
    void createReview_whenOrderItemNotFound_shouldThrowNotFoundException() {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .orderItemId(999L)
                .rating(5)
                .build();

        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Chi tiết đơn hàng (OrderItem) với ID 999 không tồn tại");

        verify(reviewRepository, never()).save(any());
    }
}
