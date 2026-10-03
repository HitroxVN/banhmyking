package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.InboxType;
import com.banhmyking.banhmyking.event.OrderChangeKind;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeRoutingTest {

    /** Đơn của khách 5, cơ sở 1, shipper 7; trước đó ở cơ sở 2 với shipper 8. */
    private static OrderChangedEvent order(Long previousStoreId, Long previousShipperId) {
        return new OrderChangedEvent(10L, "BMK-1", OrderChangeKind.STATUS_CHANGED,
                OrderStatus.CONFIRMED, OrderStatus.PREPARING, 5L, 1L, 7L, previousStoreId, previousShipperId);
    }

    @Test
    void customerReceivesOnlyOwnOrders() {
        assertThat(RealtimeRouting.receives(new RealtimeUser(5L, RoleName.CUSTOMER, null), order(null, null))).isTrue();
        assertThat(RealtimeRouting.receives(new RealtimeUser(6L, RoleName.CUSTOMER, null), order(null, null))).isFalse();
    }

    @Test
    void staffAndManagerReceiveOrdersOfTheirStoreIncludingOrdersMovedAway() {
        for (RoleName role : new RoleName[] {RoleName.STAFF, RoleName.MANAGER}) {
            assertThat(RealtimeRouting.receives(new RealtimeUser(20L, role, 1L), order(null, null))).isTrue();
            assertThat(RealtimeRouting.receives(new RealtimeUser(20L, role, 2L), order(null, null))).isFalse();
            assertThat(RealtimeRouting.receives(new RealtimeUser(20L, role, 2L), order(2L, null))).isTrue();
            assertThat(RealtimeRouting.receives(new RealtimeUser(20L, role, null), order(null, null))).isFalse();
        }
    }

    @Test
    void shipperReceivesWhenAssignedOrJustRemoved() {
        assertThat(RealtimeRouting.receives(new RealtimeUser(7L, RoleName.SHIPPER, 1L), order(null, null))).isTrue();
        assertThat(RealtimeRouting.receives(new RealtimeUser(8L, RoleName.SHIPPER, 1L), order(null, 8L))).isTrue();
        assertThat(RealtimeRouting.receives(new RealtimeUser(9L, RoleName.SHIPPER, 1L), order(null, 8L))).isFalse();
    }

    @Test
    void adminReceivesEverything() {
        RealtimeUser admin = new RealtimeUser(1L, RoleName.ADMIN, null);
        assertThat(RealtimeRouting.receives(admin, order(null, null))).isTrue();
        assertThat(RealtimeRouting.receives(admin, new InboxChangedEvent(InboxType.FEEDBACK, 3L))).isTrue();
    }

    @Test
    void inboxGoesToAdminAndManagerOfThatStoreOrChainWideOnly() {
        InboxChangedEvent storeOne = new InboxChangedEvent(InboxType.JOB_APPLICATION, 1L);
        InboxChangedEvent chainWide = new InboxChangedEvent(InboxType.FEEDBACK, null);

        assertThat(RealtimeRouting.receives(new RealtimeUser(20L, RoleName.MANAGER, 1L), storeOne)).isTrue();
        assertThat(RealtimeRouting.receives(new RealtimeUser(20L, RoleName.MANAGER, 2L), storeOne)).isFalse();
        assertThat(RealtimeRouting.receives(new RealtimeUser(20L, RoleName.MANAGER, 2L), chainWide)).isTrue();
        assertThat(RealtimeRouting.receives(new RealtimeUser(21L, RoleName.STAFF, 1L), storeOne)).isFalse();
        assertThat(RealtimeRouting.receives(new RealtimeUser(7L, RoleName.SHIPPER, 1L), storeOne)).isFalse();
        assertThat(RealtimeRouting.receives(new RealtimeUser(5L, RoleName.CUSTOMER, null), chainWide)).isFalse();
    }
}
