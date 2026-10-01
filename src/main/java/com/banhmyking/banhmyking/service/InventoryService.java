package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Product;

public interface InventoryService {

    /** Chặn sớm ở giỏ hàng/đặt đơn; sản phẩm không quản tồn thì bỏ qua. */
    void assertEnough(Product product, int quantity);

    /** Trừ tồn khi đơn được xác nhận. Không đủ hàng → BusinessException, rollback cả đơn. */
    void decreaseForOrder(Order order);

    /**
     * Như {@link #decreaseForOrder} nhưng KHÔNG ném lỗi khi thiếu hàng: trả false và để nguyên tồn kho.
     * Dùng cho luồng đã thu tiền (webhook SePay, đối soát tay) — không được rollback việc ghi nhận tiền.
     */
    boolean tryDecreaseForOrder(Order order);

    /** Hoàn tồn khi đơn bị huỷ/giao thất bại. Gọi lại nhiều lần vẫn chỉ hoàn một lần. */
    void restoreForOrder(Order order);

    /** Trả về số tồn sau khi thay đổi. */
    int adjustStock(Long productId, StockChangeRequest request, Long actorId);

    PageResponse<StockMovementResponse> getMovements(Long productId, int page, int size);
}
