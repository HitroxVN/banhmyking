package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest;
import com.banhmyking.banhmyking.dto.promotion.PromotionResponse;
import com.banhmyking.banhmyking.dto.promotion.UpdatePromotionRequest;
import com.banhmyking.banhmyking.dto.promotion.ValidatePromotionRequest;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.PromotionUsage;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.DiscountType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.PromotionRepository;
import com.banhmyking.banhmyking.repository.PromotionUsageRepository;
import com.banhmyking.banhmyking.service.PromotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionServiceImpl implements PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int incrementUsedCountAtomic(Long promotionId) {
        log.info("Executing atomic increment for promotion ID: {}", promotionId);
        int affectedRows = promotionRepository.incrementUsedCountAtomic(promotionId);
        if (affectedRows == 0) {
            log.warn("Atomic increment failed for promotion ID: {}. Affected rows: 0 (out of usage limit or inactive)", promotionId);
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi đã hết lượt sử dụng");
        }
        return affectedRows;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionUsage redeemPromotion(Promotion promotion, User user, Order order, BigDecimal discountApplied) {
        if (promotion == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi không tồn tại");
        }

        // Atomic update on DB level to prevent race conditions
        incrementUsedCountAtomic(promotion.getId());

        PromotionUsage usage = PromotionUsage.builder()
                .promotion(promotion)
                .user(user)
                .order(order)
                .discountApplied(discountApplied)
                .build();

        return promotionUsageRepository.save(usage);
    }

    @Override
    @Transactional(readOnly = true)
    public Promotion validateForOrder(String code, Long userId, BigDecimal orderSubtotal) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }

        String normalizedCode = code.trim().toUpperCase();
        Promotion promotion = promotionRepository.findByCodeAndActiveTrue(normalizedCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã khuyến mãi '" + normalizedCode + "' không tồn tại hoặc đã hết hiệu lực"));

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
        if (promotion.getMinOrderAmount() != null && orderSubtotal.compareTo(promotion.getMinOrderAmount()) < 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    String.format("Đơn hàng chưa đạt giá trị tối thiểu %,.0fđ để áp dụng mã giảm giá",
                            promotion.getMinOrderAmount().doubleValue()));
        }

        if (userId != null) {
            if (promotionUsageRepository.findByPromotionIdAndUserId(promotion.getId(), userId).isPresent()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bạn đã sử dụng mã khuyến mãi '" + normalizedCode + "' trước đó");
            }
        }

        return promotion;
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse validatePromotion(ValidatePromotionRequest request) {
        Long userId = request.getUserId();
        if (userId == null) {
            userId = getCurrentUserIdSafely();
        }

        Promotion promotion = validateForOrder(request.getCode(), userId, request.getOrderAmount());

        BigDecimal discountApplied = computeDiscount(promotion, request.getOrderAmount());

        PromotionResponse response = toPromotionResponse(promotion);
        response.setDiscountApplied(discountApplied);
        return response;
    }

    private Long getCurrentUserIdSafely() {
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                Object principal = auth.getPrincipal();
                if (principal instanceof Long id) {
                    return id;
                } else if (principal instanceof String s) {
                    return Long.valueOf(s);
                } else if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
                    return Long.valueOf(ud.getUsername());
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionResponse testRedeemAtomic(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với ID: " + id));

        // Gọi hàm atomic increment trên DB
        incrementUsedCountAtomic(promotion.getId());

        // Refresh entity from DB to get updated usedCount
        promotion = promotionRepository.findById(id).orElse(promotion);

        log.info("Test Atomic Redemption success for promotion {}. New usedCount: {}", promotion.getCode(), promotion.getUsedCount());
        return toPromotionResponse(promotion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionResponse createPromotion(CreatePromotionRequest request) {
        String code = request.getCode().trim().toUpperCase();

        // Check unique code
        if (promotionRepository.findByCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã khuyến mãi '" + code + "' đã tồn tại");
        }

        // Validate business logic: startsAt < endsAt & PERCENTAGE requires maxDiscountAmount
        validatePromotionData(code, request.getDiscountType(), request.getValue(),
                request.getMaxDiscountAmount(), request.getStartsAt(), request.getEndsAt());
        if (request.getEndsAt().isBefore(request.getStartsAt())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian kết thúc phải sau thời gian bắt đầu");
        }

        if (request.getDiscountType() == DiscountType.PERCENTAGE) {
            if (request.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phần trăm giảm giá không thể vượt quá 100%");
            }
        }

        Promotion promotion = Promotion.builder()
                .code(code)
                .description(request.getDescription())
                .discountType(request.getDiscountType())
                .value(request.getValue())
                .maxDiscountAmount(request.getMaxDiscountAmount())
                .minOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO)
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .maxUsage(request.getMaxUsage() != null ? request.getMaxUsage() : 0)
                .usedCount(0)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        promotion = promotionRepository.save(promotion);
        log.info("Created new promotion: {} (ID: {})", code, promotion.getId());

        return toPromotionResponse(promotion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PromotionResponse updatePromotion(Long id, UpdatePromotionRequest request) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với ID: " + id));

        String code = request.getCode().trim().toUpperCase();

        // Check unique code (excluding current ID)
        Optional<Promotion> existingWithCode = promotionRepository.findByCode(code);
        if (existingWithCode.isPresent() && !existingWithCode.get().getId().equals(id)) {
            throw new BusinessException(ErrorCode.CONFLICT, "Mã khuyến mãi '" + code + "' đã tồn tại");
        }

        // Validate business logic: startsAt < endsAt & PERCENTAGE requires maxDiscountAmount
        validatePromotionData(code, request.getDiscountType(), request.getValue(),
                request.getMaxDiscountAmount(), request.getStartsAt(), request.getEndsAt());

        promotion.setCode(code);
        promotion.setDescription(request.getDescription());
        promotion.setDiscountType(request.getDiscountType());
        promotion.setValue(request.getValue());
        promotion.setMaxDiscountAmount(request.getMaxDiscountAmount());
        promotion.setMinOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO);
        promotion.setStartsAt(request.getStartsAt());
        promotion.setEndsAt(request.getEndsAt());
        promotion.setMaxUsage(request.getMaxUsage() != null ? request.getMaxUsage() : 0);
        promotion.setActive(request.getActive() != null ? request.getActive() : true);

        promotion = promotionRepository.save(promotion);
        log.info("Updated promotion ID: {} ({})", id, code);

        return toPromotionResponse(promotion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePromotion(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với ID: " + id));

        if (promotionUsageRepository.existsByPromotionId(id)) {
            log.info("Promotion ID {} has associated usages. Performing soft deactivation (active = false).", id);
            promotion.setActive(false);
            promotionRepository.save(promotion);
        } else {
            log.info("Promotion ID {} has no usages. Deleting permanently.", id);
            promotionRepository.delete(promotion);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromotionResponse> getAllPromotions() {
        return promotionRepository.findAll().stream()
                .map(this::toPromotionResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionResponse getPromotionById(Long id) {
        Promotion promotion = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mã khuyến mãi với ID: " + id));
        return toPromotionResponse(promotion);
    }

    private void validatePromotionData(String code, DiscountType discountType, BigDecimal value,
                                       BigDecimal maxDiscountAmount, LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày bắt đầu phải nhỏ hơn ngày kết thúc");
        }

        if (discountType == DiscountType.PERCENTAGE) {
            if (maxDiscountAmount == null || maxDiscountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Loại giảm giá PERCENTAGE bắt buộc phải truyền giá trị maxDiscountAmount lớn hơn 0");
            }
            if (value != null && value.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phần trăm giảm giá không thể vượt quá 100%");
            }
        }
    }

    private BigDecimal computeDiscount(Promotion promotion, BigDecimal orderAmount) {
        DiscountType type = promotion.getDiscountType();
        BigDecimal value = promotion.getValue() != null ? promotion.getValue() : BigDecimal.ZERO;

        if (type == null) {
            return BigDecimal.ZERO;
        }

        return switch (type) {
            case PERCENTAGE -> {
                BigDecimal discount = orderAmount.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                if (promotion.getMaxDiscountAmount() != null && discount.compareTo(promotion.getMaxDiscountAmount()) > 0) {
                    yield promotion.getMaxDiscountAmount();
                }
                yield discount;
            }
            case FIXED_AMOUNT -> value.min(orderAmount);
            case FREE_SHIP -> value;
        };
    }

    private PromotionResponse toPromotionResponse(Promotion promotion) {
        return PromotionResponse.builder()
                .id(promotion.getId())
                .code(promotion.getCode())
                .description(promotion.getDescription())
                .discountType(promotion.getDiscountType())
                .value(promotion.getValue())
                .maxDiscountAmount(promotion.getMaxDiscountAmount())
                .minOrderAmount(promotion.getMinOrderAmount())
                .startsAt(promotion.getStartsAt())
                .endsAt(promotion.getEndsAt())
                .maxUsage(promotion.getMaxUsage())
                .usedCount(promotion.getUsedCount())
                .active(promotion.isActive())
                .createdAt(promotion.getCreatedAt())
                .build();
    }
}
