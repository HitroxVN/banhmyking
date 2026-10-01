package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.catalog.StockChangeRequest;
import com.banhmyking.banhmyking.dto.catalog.StockMovementResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.store.StoreStockResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Product;
import java.util.List;
import java.util.Map;

/** Tồn kho và hết món theo từng cơ sở (spec §2.2, §3.6). */
public interface InventoryService {

    /** Trừ tồn tại {@code order.getStore()} khi đơn được xác nhận. Không đủ → BusinessException. */
    void decreaseForOrder(Order order);

    /** Như decreaseForOrder nhưng trả false thay vì ném lỗi (luồng đã thu tiền). */
    boolean tryDecreaseForOrder(Order order);

    /** Hoàn tồn về đúng cơ sở của đơn; gọi lại nhiều lần vẫn chỉ hoàn một lần. */
    void restoreForOrder(Order order);

    /** Tên các món không bán được tại cơ sở (tắt khỏi thực đơn chuỗi, hết món, hoặc thiếu tồn). */
    List<String> unavailableItems(Long storeId, Map<Product, Integer> quantities);

    List<StoreStockResponse> listStoreStock(Long storeId);

    StoreStockResponse setAvailability(Long storeId, Long productId, boolean available);

    StoreStockResponse adjustStock(Long storeId, Long productId, StockChangeRequest request, Long actorId);

    PageResponse<StockMovementResponse> getMovements(Long storeId, Long productId, int page, int size);
}
