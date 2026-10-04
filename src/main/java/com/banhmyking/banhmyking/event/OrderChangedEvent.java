package com.banhmyking.banhmyking.event;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.enums.OrderStatus;

import java.util.Objects;

/**
 * Đơn hàng vừa thay đổi (spec realtime §2.1). Phát trong giao dịch ghi lịch sử đơn,
 * {@code RealtimeEventListener} chỉ gửi đi sau khi giao dịch commit.
 */
public record OrderChangedEvent(
        Long orderId,
        String orderCode,
        OrderChangeKind kind,
        OrderStatus fromStatus,
        OrderStatus toStatus,
        Long customerId,
        Long storeId,
        Long shipperId,
        Long previousStoreId,
        Long previousShipperId) {

    /**
     * @param kind null = suy ra từ trạng thái (khác nhau → STATUS_CHANGED, giống nhau → UPDATED)
     * @param previousStoreId cơ sở trước khi đổi; trùng cơ sở hiện tại thì bỏ (null)
     * @param previousShipperId shipper trước khi đổi; trùng shipper hiện tại thì bỏ (null)
     */
    public static OrderChangedEvent of(Order order, OrderStatus fromStatus, OrderStatus toStatus,
                                       OrderChangeKind kind, Long previousStoreId, Long previousShipperId) {
        OrderChangeKind resolved = kind != null ? kind
                : (fromStatus != toStatus ? OrderChangeKind.STATUS_CHANGED : OrderChangeKind.UPDATED);
        Long storeId = order.getStore() == null ? null : order.getStore().getId();
        Long shipperId = order.getShipper() == null ? null : order.getShipper().getId();
        Long customerId = order.getUser() == null ? null : order.getUser().getId();
        return new OrderChangedEvent(order.getId(), order.getOrderCode(), resolved, fromStatus, toStatus,
                customerId, storeId, shipperId,
                Objects.equals(previousStoreId, storeId) ? null : previousStoreId,
                Objects.equals(previousShipperId, shipperId) ? null : previousShipperId);
    }
}
