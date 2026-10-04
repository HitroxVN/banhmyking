package com.banhmyking.banhmyking.event;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderChangedEventTest {

    private static Order order(Long storeId, Long shipperId, Long customerId) {
        Order order = new Order();
        order.setId(10L);
        order.setOrderCode("BMK-20261003-ABCDE");
        if (storeId != null) {
            Store store = new Store();
            store.setId(storeId);
            order.setStore(store);
        }
        if (shipperId != null) {
            User shipper = new User();
            shipper.setId(shipperId);
            order.setShipper(shipper);
        }
        if (customerId != null) {
            User customer = new User();
            customer.setId(customerId);
            order.setUser(customer);
        }
        return order;
    }

    @Test
    void derivesStatusChangedWhenStatusesDiffer() {
        OrderChangedEvent event = OrderChangedEvent.of(order(1L, null, 5L),
                OrderStatus.CONFIRMED, OrderStatus.PREPARING, null, null, null);

        assertThat(event.kind()).isEqualTo(OrderChangeKind.STATUS_CHANGED);
        assertThat(event.orderId()).isEqualTo(10L);
        assertThat(event.orderCode()).isEqualTo("BMK-20261003-ABCDE");
        assertThat(event.customerId()).isEqualTo(5L);
        assertThat(event.storeId()).isEqualTo(1L);
        assertThat(event.shipperId()).isNull();
    }

    @Test
    void derivesUpdatedWhenStatusUnchangedAndNoExplicitKind() {
        OrderChangedEvent event = OrderChangedEvent.of(order(1L, null, 5L),
                OrderStatus.PENDING, OrderStatus.PENDING, null, null, null);

        assertThat(event.kind()).isEqualTo(OrderChangeKind.UPDATED);
    }

    @Test
    void explicitKindWins() {
        OrderChangedEvent event = OrderChangedEvent.of(order(1L, null, 5L),
                OrderStatus.PENDING, OrderStatus.PENDING, OrderChangeKind.CREATED, null, null);

        assertThat(event.kind()).isEqualTo(OrderChangeKind.CREATED);
    }

    @Test
    void previousIdsAreKeptOnlyWhenTheyActuallyChanged() {
        OrderChangedEvent moved = OrderChangedEvent.of(order(2L, null, 5L),
                OrderStatus.PENDING, OrderStatus.PENDING, OrderChangeKind.STORE_TRANSFERRED, 1L, 7L);
        assertThat(moved.previousStoreId()).isEqualTo(1L);
        assertThat(moved.previousShipperId()).isEqualTo(7L);

        OrderChangedEvent same = OrderChangedEvent.of(order(2L, 7L, 5L),
                OrderStatus.PENDING, OrderStatus.PENDING, OrderChangeKind.STORE_TRANSFERRED, 2L, 7L);
        assertThat(same.previousStoreId()).isNull();
        assertThat(same.previousShipperId()).isNull();
    }

    @Test
    void toleratesOrderWithoutStoreShipperOrCustomer() {
        OrderChangedEvent event = OrderChangedEvent.of(order(null, null, null),
                OrderStatus.PENDING, OrderStatus.CANCELLED, null, null, null);

        assertThat(event.storeId()).isNull();
        assertThat(event.shipperId()).isNull();
        assertThat(event.customerId()).isNull();
    }
}
