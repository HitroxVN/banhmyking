package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.InboxType;
import com.banhmyking.banhmyking.event.OrderChangeKind;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeHubTest {

    /** Sink giả: ghi lại tin đã nhận; failOnSend = mô phỏng trình duyệt đã đóng kết nối. */
    static final class FakeSink implements RealtimeSink {
        final List<String> events = new ArrayList<>();
        final List<Object> payloads = new ArrayList<>();
        final List<String> comments = new ArrayList<>();
        boolean failOnSend;
        boolean closed;

        @Override
        public void send(String eventName, Object data) throws IOException {
            if (failOnSend) throw new IOException("broken pipe");
            events.add(eventName);
            payloads.add(data);
        }

        @Override
        public void sendComment(String comment) throws IOException {
            if (failOnSend) throw new IOException("broken pipe");
            comments.add(comment);
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static OrderChangedEvent orderOfCustomer5AtStore1() {
        return new OrderChangedEvent(10L, "BMK-1", OrderChangeKind.CREATED,
                OrderStatus.PENDING, OrderStatus.PENDING, 5L, 1L, null, null, null);
    }

    @Test
    void registerSendsReadyWithConnectionId() {
        RealtimeHub hub = new RealtimeHub();
        FakeSink sink = new FakeSink();

        String id = hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), sink);

        assertThat(sink.events).containsExactly("ready");
        assertThat(sink.payloads.get(0)).isEqualTo(java.util.Map.of("connectionId", id));
        assertThat(hub.connectionCount()).isEqualTo(1);
    }

    @Test
    void orderSignalGoesOnlyToMatchingConnections() {
        RealtimeHub hub = new RealtimeHub();
        FakeSink owner = new FakeSink();
        FakeSink stranger = new FakeSink();
        FakeSink kitchen = new FakeSink();
        hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), owner);
        hub.register(new RealtimeUser(6L, RoleName.CUSTOMER, null), stranger);
        hub.register(new RealtimeUser(20L, RoleName.STAFF, 1L), kitchen);

        hub.publish(orderOfCustomer5AtStore1());

        assertThat(owner.events).containsExactly("ready", "order");
        assertThat(stranger.events).containsExactly("ready");
        assertThat(kitchen.events).containsExactly("ready", "order");
        OrderSignal signal = (OrderSignal) owner.payloads.get(1);
        assertThat(signal).isEqualTo(new OrderSignal("BMK-1", OrderStatus.PENDING, OrderChangeKind.CREATED, 1L, null, null));
    }

    @Test
    void inboxSignalGoesToAdminOnly() {
        RealtimeHub hub = new RealtimeHub();
        FakeSink admin = new FakeSink();
        FakeSink customer = new FakeSink();
        hub.register(new RealtimeUser(1L, RoleName.ADMIN, null), admin);
        hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), customer);

        hub.publish(new InboxChangedEvent(InboxType.FEEDBACK, null));

        assertThat(admin.events).containsExactly("ready", "inbox");
        assertThat(admin.payloads.get(1)).isEqualTo(new InboxSignal(InboxType.FEEDBACK));
        assertThat(customer.events).containsExactly("ready");
    }

    @Test
    void sixthConnectionOfSameUserClosesTheOldest() {
        RealtimeHub hub = new RealtimeHub();
        List<FakeSink> sinks = new ArrayList<>();
        for (int i = 0; i < RealtimeHub.MAX_CONNECTIONS_PER_USER + 1; i++) {
            FakeSink sink = new FakeSink();
            sinks.add(sink);
            hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), sink);
        }

        assertThat(hub.connectionCount()).isEqualTo(RealtimeHub.MAX_CONNECTIONS_PER_USER);
        assertThat(sinks.get(0).closed).isTrue();
        assertThat(sinks.subList(1, sinks.size())).noneMatch(s -> s.closed);
    }

    @Test
    void brokenConnectionIsDroppedWithoutAffectingOthers() {
        RealtimeHub hub = new RealtimeHub();
        FakeSink broken = new FakeSink();
        FakeSink healthy = new FakeSink();
        hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), broken);
        hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), healthy);
        broken.failOnSend = true;

        hub.publish(orderOfCustomer5AtStore1());

        assertThat(hub.connectionCount()).isEqualTo(1);
        assertThat(broken.closed).isTrue();
        assertThat(healthy.events).containsExactly("ready", "order");
    }

    @Test
    void heartbeatPingsEveryConnectionAndDropsDeadOnes() {
        RealtimeHub hub = new RealtimeHub();
        FakeSink alive = new FakeSink();
        FakeSink dead = new FakeSink();
        hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), alive);
        hub.register(new RealtimeUser(6L, RoleName.CUSTOMER, null), dead);
        dead.failOnSend = true;

        hub.heartbeat();

        assertThat(alive.comments).containsExactly("ping");
        assertThat(hub.connectionCount()).isEqualTo(1);
    }

    @Test
    void unregisterRemovesConnection() {
        RealtimeHub hub = new RealtimeHub();
        String id = hub.register(new RealtimeUser(5L, RoleName.CUSTOMER, null), new FakeSink());

        hub.unregister(id);

        assertThat(hub.connectionCount()).isZero();
    }
}
