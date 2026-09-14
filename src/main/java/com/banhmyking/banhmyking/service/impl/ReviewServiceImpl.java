package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.review.CreateReviewRequest;
import com.banhmyking.banhmyking.dto.review.ReviewResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Review;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.ReviewRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewResponse createReview(Long userId, CreateReviewRequest request) {
        log.info("User {} is creating review for orderItem {}", userId, request.getOrderItemId());

        // 1. Kiểm tra rating
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Điểm đánh giá phải nằm trong khoảng từ 1 đến 5");
        }

        // 2. Tìm OrderItem
        OrderItem orderItem = orderItemRepository.findById(request.getOrderItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Chi tiết đơn hàng (OrderItem) với ID " + request.getOrderItemId() + " không tồn tại"));

        Order order = orderItem.getOrder();
        if (order == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Chi tiết đơn hàng không thuộc về đơn hàng hợp lệ");
        }

        // 3. Kiểm tra ownership (người dùng đánh giá đơn của chính mình)
        User orderUser = order.getUser();
        if (orderUser == null || !orderUser.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền đánh giá món ăn trong đơn hàng của người khác");
        }

        // 4. Kiểm tra trạng thái đơn hàng (chỉ cho phép khi DELIVERED)
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Chỉ có thể đánh giá món ăn khi đơn hàng đã được giao thành công (DELIVERED)");
        }

        // 5. Kiểm tra Unique Constraint (mỗi order_item chỉ được phép đánh giá đúng 1 lần)
        if (reviewRepository.existsByOrderItemId(request.getOrderItemId())) {
            throw new BusinessException(ErrorCode.CONFLICT, "Món ăn trong đơn hàng này đã được đánh giá trước đó");
        }

        // 6. Tạo và lưu Review entity
        Review review = new Review();
        review.setProduct(orderItem.getProduct());
        review.setUser(orderUser);
        review.setOrderItem(orderItem);
        review.setRating(request.getRating());
        review.setComment(request.getComment() != null ? request.getComment().trim() : null);

        review = reviewRepository.save(review);
        log.info("Created review ID {} for orderItem {}", review.getId(), request.getOrderItemId());

        return toReviewResponse(review);
    }

    private ReviewResponse toReviewResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct() != null ? review.getProduct().getId() : null)
                .productName(review.getOrderItem() != null ? review.getOrderItem().getProductName()
                        : (review.getProduct() != null ? review.getProduct().getName() : null))
                .userId(review.getUser() != null ? review.getUser().getId() : null)
                .userFullName(review.getUser() != null ? review.getUser().getFullName() : null)
                .orderItemId(review.getOrderItem() != null ? review.getOrderItem().getId() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
