package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest;
import com.banhmyking.banhmyking.dto.promotion.PromotionResponse;
import com.banhmyking.banhmyking.dto.promotion.PublicPromotionResponse;
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
     * Kiểm tra tính hợp lệ của mã giảm giá khi áp dụng vào đơn hàng (hạn dùng, lượt dùng, đơn tối thiểu, user chưa dùng).
     */
    Promotion validateForOrder(String code, Long userId, BigDecimal orderSubtotal);

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
     * Danh sách mã giảm giá KHÁCH đang dùng được, để khách chọn thay vì gõ mù.
     * Đã lọc theo hiệu lực (active, trong khoảng ngày, còn lượt) và loại các mã
     * chính khách này đã dùng. Trả về bản rút gọn {@link PublicPromotionResponse},
     * không lộ số lượt / id nội bộ.
     */
    List<PublicPromotionResponse> getPublicPromotions();

    /**
     * Lấy thông tin chi tiết mã giảm giá theo ID.
     */
    PromotionResponse getPromotionById(Long id);

    /**
     * Hoàn lại lượt đã tiêu cho mã của đơn bị huỷ / giao thất bại: xoá bản ghi PromotionUsage
     * và giảm usedCount. Idempotent — gọi lại trên đơn không còn usage thì không làm gì.
     */
    void releaseForOrder(Order order);

    /**
     * Kiểm tra lại mã của đơn vẫn còn hiệu lực ở bước xác nhận (active, trong khoảng ngày,
     * không hết lượt). Bỏ qua kiểm tra "user đã dùng" vì usage của chính đơn này đã tồn tại.
     */
    void assertStillValidForConfirm(Order order);
}
