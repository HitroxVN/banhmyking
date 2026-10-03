package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.event.OrderChangeKind;
import com.banhmyking.banhmyking.event.OrderChangedEvent;

/** Payload tin "order" gửi xuống trình duyệt — chỉ đủ để trang biết có nên tải lại hay không. */
public record OrderSignal(String orderCode, OrderStatus status, OrderChangeKind kind, Long storeId, Long shipperId,
                          Long previousStoreId) {

    static OrderSignal from(OrderChangedEvent event) {
        return new OrderSignal(event.orderCode(), event.toStatus(), event.kind(), event.storeId(), event.shipperId(),
                event.previousStoreId());
    }
}
