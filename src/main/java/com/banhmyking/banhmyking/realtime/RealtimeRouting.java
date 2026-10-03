package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.OrderChangedEvent;

import java.util.Objects;

/**
 * Ai nhận tin gì (spec realtime §3.2) — khớp phạm vi dữ liệu mỗi vai trò vốn xem được qua API.
 * Tin chỉ là tín hiệu, dữ liệu thật vẫn đi qua API có phân quyền.
 */
final class RealtimeRouting {

    private RealtimeRouting() {
    }

    static boolean receives(RealtimeUser user, OrderChangedEvent event) {
        return switch (user.role()) {
            case ADMIN -> true;
            case CUSTOMER -> user.userId() != null && user.userId().equals(event.customerId());
            case STAFF, MANAGER -> user.storeId() != null
                    && (user.storeId().equals(event.storeId()) || user.storeId().equals(event.previousStoreId()));
            case SHIPPER -> user.userId() != null
                    && (user.userId().equals(event.shipperId()) || user.userId().equals(event.previousShipperId()));
        };
    }

    static boolean receives(RealtimeUser user, InboxChangedEvent event) {
        return switch (user.role()) {
            case ADMIN -> true;
            case MANAGER -> event.storeId() == null || Objects.equals(user.storeId(), event.storeId());
            default -> false;
        };
    }
}
