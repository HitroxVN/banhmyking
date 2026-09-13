package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest;
import com.banhmyking.banhmyking.dto.promotion.PromotionResponse;
import com.banhmyking.banhmyking.dto.promotion.ValidatePromotionRequest;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Promotion;
import com.banhmyking.banhmyking.entity.PromotionUsage;
import com.banhmyking.banhmyking.entity.User;

import java.math.BigDecimal;
import java.util.List;

import com.banhmyking.banhmyking.dto.promotion.UpdatePromotionRequest;

public interface PromotionService {

    /**
     * Trừ lượt sử dụng (redemption) bằng câu lệnh UPDATE nguyên tử (atomic) trên DB.
     * Bắn lỗi BusinessException nếu mã đã hết lượt sử dụng (affected rows == 0).
     *
     * @param promotionId ID của mã khuyến mãi
     * @return số dòng bị ảnh hưởng (1 nếu thành công, 0 nếu hết lượt)
     */
    int incrementUsedCountAtomic(Long promotionId);

    /**
     * Trừ lượt sử dụng của promotion theo cách nguyên tử (atomic update)
     * và ghi nhận PromotionUsage cho đơn hàng.
     * Bắn lỗi BusinessException nếu số dòng bị ảnh hưởng bằng 0.
     */
    PromotionUsage redeemPromotion(Promotion promotion, User user, Order order, BigDecimal discountApplied);

    /**
     * Kiểm tra mã giảm giá và tính số tiền thực tế được giảm cho đơn hàng.
     */
    PromotionResponse validatePromotion(ValidatePromotionRequest request);

    /**
     * Test trực tiếp logic Atomic Redemption của PROMO-01 qua API Swagger.
     * Tăng lượt sử dụng lên 1 bằng UPDATE nguyên tử trên DB.
     */
    PromotionResponse testRedeemAtomic(Long id);

    /**
     * Admin tạo mới mã giảm giá.
     */
    PromotionResponse createPromotion(CreatePromotionRequest request);

    /**
     * Admin cập nhật thông tin mã giảm giá.
     */
    PromotionResponse updatePromotion(Long id, UpdatePromotionRequest request);

    /**
     * Admin xóa mã giảm giá.
     */
    void deletePromotion(Long id);

    /**
     * Lấy danh sách tất cả mã giảm giá (Admin).
     */
    List<PromotionResponse> getAllPromotions();

    /**
     * Lấy thông tin chi tiết mã giảm giá theo ID.
     */
    PromotionResponse getPromotionById(Long id);
}
