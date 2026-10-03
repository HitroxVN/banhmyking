# Realtime đơn hàng & hộp thư (dự án con E) — Kế hoạch triển khai

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thay polling bằng luồng Server-Sent Events để bếp, shipper, khách và admin thấy đơn hàng / hộp thư thay đổi tức thì, kèm âm báo + nháy tab, có polling 30 giây dự phòng.

**Architecture:** Backend phát sự kiện Spring (`OrderChangedEvent`, `InboxChangedEvent`) ngay chỗ ghi lịch sử đơn / lưu phản hồi; `RealtimeEventListener` (AFTER_COMMIT) chuyển cho `RealtimeHub` — giữ kết nối SSE trong bộ nhớ và lọc người nhận theo vai trò/cơ sở. Frontend mở **một** luồng `fetch` có header JWT trong `RealtimeProvider`; các trang dùng `useLiveRefresh` để gọi lại API REST sẵn có khi có tín hiệu.

**Tech Stack:** Spring Boot 4.1.1 (Spring MVC `SseEmitter`, `@TransactionalEventListener`, `@Scheduled`), JUnit 5 + Mockito + MockMvc; React 19 + Vite 8 + TypeScript 6, Vitest (mới, dev).

**Spec:** `docs/superpowers/specs/2026-10-03-realtime-don-hang-design.md`

## Global Constraints

- **Không commit.** Người dùng tự commit — mọi bước "Commit" trong kế hoạch này thay bằng: chạy `git status` để liệt kê file đã đổi, không chạy `git add`/`git commit`.
- Nhánh làm việc: `feature/realtime-don-hang` (đã tạo).
- Backend **không thêm dependency**; frontend chỉ thêm `vitest` vào `devDependencies`.
- Không migration, không đổi schema.
- Sự kiện chỉ là **tín hiệu** — payload SSE chỉ có `orderCode`, `status`, `kind`, `storeId`, `shipperId` (đơn) hoặc `type` (hộp thư).
- Endpoint: `GET /api/v1/realtime/stream`, `text/event-stream`, timeout 30 phút, tối đa 5 kết nối/người dùng (đóng kết nối cũ nhất), `: ping` mỗi 25 giây.
- Polling dự phòng 30 giây khi luồng không `live`; gộp tín hiệu 300ms; lịch nối lại `1s → 2s → 5s → 10s → 30s`, jitter ±20%, ổn định ≥ 60 giây thì đặt lại.
- Bếp chỉ kêu khi `kind = CREATED` thuộc cơ sở đang chọn; shipper giữ chuông có sẵn trong `ShipperOrdersPage`.
- Toast khách chỉ cho vai trò `CUSTOMER`, bỏ qua khi đang ở `/orders/{mã đơn}`.
- Comment trong code viết tiếng Việt, ngắn gọn, theo mật độ comment của file xung quanh.
- Lệnh backend chạy từ thư mục gốc `C:\Workspace\banhmyking` bằng `./mvnw` (Git Bash) hoặc `.\mvnw.cmd` (PowerShell). Lệnh frontend chạy từ `frontend/`.
- Test backend kiểu `@SpringBootTest` dùng DB dev (MySQL cổng 3307) đang chạy, giống các test sẵn có.

## Bản đồ file

**Backend — tạo mới** (gốc `src/main/java/com/banhmyking/banhmyking/`):
| File | Trách nhiệm |
|---|---|
| `event/OrderChangeKind.java` | enum loại thay đổi đơn |
| `event/OrderChangedEvent.java` | record sự kiện đơn + `of(...)` tính `kind`, `previous*` |
| `event/InboxType.java` | enum `FEEDBACK`, `JOB_APPLICATION` |
| `event/InboxChangedEvent.java` | record sự kiện hộp thư |
| `realtime/RealtimeUser.java` | record (userId, role, storeId) của một kết nối |
| `realtime/RealtimeSink.java` | interface đầu ra một kết nối (để test không cần `SseEmitter`) |
| `realtime/SseEmitterSink.java` | cài `RealtimeSink` bằng `SseEmitter` |
| `realtime/RealtimeRouting.java` | quy tắc ai nhận tin gì (hàm thuần) |
| `realtime/OrderSignal.java`, `realtime/InboxSignal.java` | payload JSON gửi xuống |
| `realtime/RealtimeHub.java` | danh sách kết nối, gửi tin, ping, giới hạn 5 |
| `realtime/RealtimeEventListener.java` | `@TransactionalEventListener(AFTER_COMMIT)` → hub |
| `controller/RealtimeController.java` | `GET /api/v1/realtime/stream` |

**Backend — sửa:** `config/SecurityConfig.java`, `service/impl/OrderServiceImpl.java`, `service/impl/PaymentServiceImpl.java`, `service/FeedbackService.java`, `service/JobApplicationService.java`.

**Backend test** (gốc `src/test/java/com/banhmyking/banhmyking/`): tạo `event/OrderChangedEventTest.java`, `realtime/RealtimeRoutingTest.java`, `realtime/RealtimeHubTest.java`, `controller/RealtimeStreamSecurityTest.java`; sửa `service/OrderOwnershipTest.java`, `OrderPriceSnapshotTest.java`, `OrderServiceApisTest.java`, `OrderServiceStateMachineTest.java`, `OrderServiceTest.java`, `OrderStoreScopeTest.java`, `PaymentServiceTest.java`, `FeedbackServiceTest.java`, `JobApplicationServiceTest.java`.

**Frontend — tạo mới** (gốc `frontend/src/`):
| File | Trách nhiệm |
|---|---|
| `realtime/types.ts` | kiểu tin realtime |
| `realtime/sseParser.ts` (+ `.test.ts`) | đọc SSE tăng dần |
| `realtime/backoff.ts` (+ `.test.ts`) | lịch chờ nối lại |
| `realtime/coalescer.ts` (+ `.test.ts`) | gộp tín hiệu 300ms |
| `realtime/realtimeClient.ts` | mở luồng fetch, nối lại, xử lý 401 |
| `context/realtimeContextDef.ts`, `context/RealtimeProvider.tsx`, `context/useRealtime.ts` | một kết nối cho cả app + hook nghe |
| `hooks/useLiveRefresh.ts` | thay `usePolling` |
| `hooks/useOrderAlerts.ts` | chuông bếp + nháy tab |
| `hooks/useOrderStatusToasts.ts` | toast cho khách |
| `utils/alertSound.ts` | `AudioContext` dùng chung + bật/tắt âm báo |
| `components/layout/AlertSoundToggle.tsx` | nút "Bật âm báo" |

**Frontend — sửa:** `package.json`, `App.tsx`, `components/ui/PageHeader.tsx`, `styles/components/page-header.css`, `styles/layout.css`, `components/layout/DashboardLayout.tsx`, `components/layout/CustomerLayout.tsx`, `utils/orderSyncChannel.ts`, `hooks/useInboxCounts.ts`, `pages/staff/StaffOrderQueuePage.tsx`, `pages/shipper/ShipperOrdersPage.tsx`, `pages/OrderTrackingPage.tsx`, `pages/OrdersPage.tsx`, `pages/admin/AdminOrdersPage.tsx`. **Xoá:** `hooks/usePolling.ts`.

---

### Task 1: Mô hình sự kiện (backend)

**Files:**
- Create: `src/main/java/com/banhmyking/banhmyking/event/OrderChangeKind.java`
- Create: `src/main/java/com/banhmyking/banhmyking/event/OrderChangedEvent.java`
- Create: `src/main/java/com/banhmyking/banhmyking/event/InboxType.java`
- Create: `src/main/java/com/banhmyking/banhmyking/event/InboxChangedEvent.java`
- Test: `src/test/java/com/banhmyking/banhmyking/event/OrderChangedEventTest.java`

**Interfaces:**
- Produces: `OrderChangedEvent(Long orderId, String orderCode, OrderChangeKind kind, OrderStatus fromStatus, OrderStatus toStatus, Long customerId, Long storeId, Long shipperId, Long previousStoreId, Long previousShipperId)`; `static OrderChangedEvent of(Order order, OrderStatus fromStatus, OrderStatus toStatus, OrderChangeKind kind, Long previousStoreId, Long previousShipperId)` — `kind == null` thì suy ra; `previous*` bằng giá trị hiện tại thì thành `null`. `InboxChangedEvent(InboxType type, Long storeId)`.

- [ ] **Step 1: Viết test hỏng**

```java
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
```

- [ ] **Step 2: Chạy test, xác nhận hỏng**

Run: `./mvnw -q test -Dtest=OrderChangedEventTest`
Expected: FAIL — lỗi biên dịch `cannot find symbol: class OrderChangedEvent`.

- [ ] **Step 3: Viết code**

`event/OrderChangeKind.java`:
```java
package com.banhmyking.banhmyking.event;

/** Loại thay đổi của đơn — frontend dựa vào đây để quyết định kêu chuông / hiện toast. */
public enum OrderChangeKind {
    CREATED,
    STATUS_CHANGED,
    SHIPPER_ASSIGNED,
    STORE_TRANSFERRED,
    UPDATED
}
```

`event/OrderChangedEvent.java`:
```java
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
```

`event/InboxType.java`:
```java
package com.banhmyking.banhmyking.event;

/** Loại mục trong hộp thư quản lý — khớp hai huy hiệu "Phản hồi" và "Ứng viên" trên menu. */
public enum InboxType {
    FEEDBACK,
    JOB_APPLICATION
}
```

`event/InboxChangedEvent.java`:
```java
package com.banhmyking.banhmyking.event;

/** Có phản hồi / hồ sơ ứng tuyển mới hoặc vừa đổi trạng thái xử lý. storeId null = toàn chuỗi. */
public record InboxChangedEvent(InboxType type, Long storeId) {
}
```

- [ ] **Step 4: Chạy test, xác nhận qua**

Run: `./mvnw -q test -Dtest=OrderChangedEventTest`
Expected: PASS (5 test).

- [ ] **Step 5: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 2: Quy tắc nhận tin + `RealtimeHub`

**Files:**
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/RealtimeUser.java`
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/RealtimeSink.java`
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/RealtimeRouting.java`
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/OrderSignal.java`
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/InboxSignal.java`
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/RealtimeHub.java`
- Test: `src/test/java/com/banhmyking/banhmyking/realtime/RealtimeRoutingTest.java`
- Test: `src/test/java/com/banhmyking/banhmyking/realtime/RealtimeHubTest.java`

**Interfaces:**
- Consumes: `OrderChangedEvent`, `InboxChangedEvent`, `OrderChangeKind`, `InboxType` (Task 1).
- Produces:
  - `record RealtimeUser(Long userId, RoleName role, Long storeId)`, `static RealtimeUser from(User user)`.
  - `interface RealtimeSink { void send(String eventName, Object data) throws IOException; void sendComment(String comment) throws IOException; void close(); }`
  - `RealtimeRouting.receives(RealtimeUser, OrderChangedEvent)`, `RealtimeRouting.receives(RealtimeUser, InboxChangedEvent)` (static boolean).
  - `record OrderSignal(String orderCode, OrderStatus status, OrderChangeKind kind, Long storeId, Long shipperId)`, `static OrderSignal from(OrderChangedEvent)`; `record InboxSignal(InboxType type)`.
  - `RealtimeHub`: `String register(RealtimeUser user, RealtimeSink sink)`, `void unregister(String connectionId)`, `void publish(OrderChangedEvent)`, `void publish(InboxChangedEvent)`, `void heartbeat()` (`@Scheduled` 25s), `int connectionCount()`, hằng `MAX_CONNECTIONS_PER_USER = 5`. Tin gửi có tên `ready` (data `{"connectionId": id}`), `order` (data `OrderSignal`), `inbox` (data `InboxSignal`); ping là comment `ping`.

- [ ] **Step 1: Viết test hỏng cho quy tắc nhận tin**

```java
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
```

- [ ] **Step 2: Viết test hỏng cho hub**

```java
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
        assertThat(signal).isEqualTo(new OrderSignal("BMK-1", OrderStatus.PENDING, OrderChangeKind.CREATED, 1L, null));
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
```

- [ ] **Step 3: Chạy test, xác nhận hỏng**

Run: `./mvnw -q test -Dtest="RealtimeRoutingTest,RealtimeHubTest"`
Expected: FAIL — lỗi biên dịch, chưa có `RealtimeHub`, `RealtimeRouting`, `RealtimeUser`.

- [ ] **Step 4: Viết code**

`realtime/RealtimeUser.java`:
```java
package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;

/** Danh tính của một kết nối realtime — chụp lúc kết nối, đổi cơ sở có hiệu lực ở lần nối lại. */
public record RealtimeUser(Long userId, RoleName role, Long storeId) {

    public static RealtimeUser from(User user) {
        return new RealtimeUser(user.getId(), user.getRole(),
                user.getStore() == null ? null : user.getStore().getId());
    }
}
```

`realtime/RealtimeSink.java`:
```java
package com.banhmyking.banhmyking.realtime;

import java.io.IOException;

/** Đầu ra của một kết nối. Tách khỏi SseEmitter để test hub không cần Spring. */
public interface RealtimeSink {

    void send(String eventName, Object data) throws IOException;

    void sendComment(String comment) throws IOException;

    void close();
}
```

`realtime/RealtimeRouting.java`:
```java
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
```

`realtime/OrderSignal.java`:
```java
package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.event.OrderChangeKind;
import com.banhmyking.banhmyking.event.OrderChangedEvent;

/** Payload tin "order" gửi xuống trình duyệt — chỉ đủ để trang biết có nên tải lại hay không. */
public record OrderSignal(String orderCode, OrderStatus status, OrderChangeKind kind, Long storeId, Long shipperId) {

    static OrderSignal from(OrderChangedEvent event) {
        return new OrderSignal(event.orderCode(), event.toStatus(), event.kind(), event.storeId(), event.shipperId());
    }
}
```

`realtime/InboxSignal.java`:
```java
package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.event.InboxType;

/** Payload tin "inbox" gửi xuống trình duyệt. */
public record InboxSignal(InboxType type) {
}
```

`realtime/RealtimeHub.java`:
```java
package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Giữ các kết nối SSE đang mở (trong bộ nhớ — chỉ đúng với một instance backend, spec §8)
 * và gửi tín hiệu tới đúng người. Lỗi gửi chỉ làm rơi kết nối đó, không bao giờ ném ra ngoài.
 */
@Slf4j
@Component
public class RealtimeHub {

    static final int MAX_CONNECTIONS_PER_USER = 5;

    private record Connection(String id, RealtimeUser user, RealtimeSink sink, long sequence) {
    }

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public String register(RealtimeUser user, RealtimeSink sink) {
        String id = UUID.randomUUID().toString();
        Connection connection = new Connection(id, user, sink, sequence.incrementAndGet());
        connections.put(id, connection);
        evictOverflow(user);
        deliver(connection, "ready", Map.of("connectionId", id));
        return id;
    }

    public void unregister(String connectionId) {
        connections.remove(connectionId);
    }

    public void publish(OrderChangedEvent event) {
        OrderSignal signal = OrderSignal.from(event);
        for (Connection connection : connections.values()) {
            if (RealtimeRouting.receives(connection.user(), event)) {
                deliver(connection, "order", signal);
            }
        }
    }

    public void publish(InboxChangedEvent event) {
        InboxSignal signal = new InboxSignal(event.type());
        for (Connection connection : connections.values()) {
            if (RealtimeRouting.receives(connection.user(), event)) {
                deliver(connection, "inbox", signal);
            }
        }
    }

    /** Giữ kết nối qua proxy và phát hiện trình duyệt đã đóng mà server chưa biết. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (Connection connection : connections.values()) {
            try {
                connection.sink().sendComment("ping");
            } catch (Exception ex) {
                drop(connection, ex);
            }
        }
    }

    public int connectionCount() {
        return connections.size();
    }

    private void deliver(Connection connection, String eventName, Object data) {
        try {
            connection.sink().send(eventName, data);
        } catch (Exception ex) {
            drop(connection, ex);
        }
    }

    private void drop(Connection connection, Exception cause) {
        log.debug("Realtime connection {} of user {} dropped: {}", connection.id(),
                connection.user().userId(), cause.toString());
        connections.remove(connection.id());
        closeQuietly(connection.sink());
    }

    /** Quá giới hạn thì đóng kết nối CŨ NHẤT — tab cũ tự chuyển polling rồi nối lại. */
    private void evictOverflow(RealtimeUser user) {
        List<Connection> mine = connections.values().stream()
                .filter(c -> c.user().userId() != null && c.user().userId().equals(user.userId()))
                .sorted(Comparator.comparingLong(Connection::sequence))
                .toList();
        for (int i = 0; i < mine.size() - MAX_CONNECTIONS_PER_USER; i++) {
            Connection oldest = mine.get(i);
            connections.remove(oldest.id());
            closeQuietly(oldest.sink());
        }
    }

    private static void closeQuietly(RealtimeSink sink) {
        try {
            sink.close();
        } catch (Exception ignored) {
            // Kết nối đã hỏng sẵn — không còn gì để đóng
        }
    }
}
```

- [ ] **Step 5: Chạy test, xác nhận qua**

Run: `./mvnw -q test -Dtest="RealtimeRoutingTest,RealtimeHubTest"`
Expected: PASS (5 + 7 test).

- [ ] **Step 6: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 3: Endpoint SSE, listener sau commit, cấu hình bảo mật

**Files:**
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/SseEmitterSink.java`
- Create: `src/main/java/com/banhmyking/banhmyking/realtime/RealtimeEventListener.java`
- Create: `src/main/java/com/banhmyking/banhmyking/controller/RealtimeController.java`
- Modify: `src/main/java/com/banhmyking/banhmyking/config/SecurityConfig.java` (khối `authorizeHttpRequests`, khoảng dòng 66)
- Test: `src/test/java/com/banhmyking/banhmyking/controller/RealtimeStreamSecurityTest.java`

**Interfaces:**
- Consumes: `RealtimeHub.register/unregister/publish`, `RealtimeUser.from`, `RealtimeSink` (Task 2); `OrderChangedEvent`, `InboxChangedEvent` (Task 1).
- Produces: `GET /api/v1/realtime/stream` (đăng nhập bất kỳ vai trò) → `text/event-stream`, header `Cache-Control: no-cache`, `X-Accel-Buffering: no`. Định dạng dòng do Spring sinh: `event:ready\ndata:{...}\n\n`, comment `:ping\n\n`.

- [ ] **Step 1: Viết test hỏng**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Luồng realtime đi qua filter chain + JWT thật (spec realtime §4, §7). DB dev, rollback sau mỗi test. */
@SpringBootTest
@Transactional
class RealtimeStreamSecurityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private User customer;

    @BeforeEach
    void setUp() {
        Filter securityChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();

        User user = new User();
        user.setEmail("realtime-" + Long.toString(System.nanoTime(), 36) + "@test.local");
        user.setPassword("not-used");
        user.setFullName("Khách realtime");
        user.setRole(RoleName.CUSTOMER);
        customer = userRepository.save(user);
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/realtime/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserGetsEventStreamStartingWithReady() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/realtime/stream")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(customer))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(result.getResponse().getContentAsString()).contains("event:ready").contains("connectionId");
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận hỏng**

Run: `./mvnw -q test -Dtest=RealtimeStreamSecurityTest`
Expected: FAIL — `authenticatedUserGetsEventStreamStartingWithReady` nhận 404 (chưa có endpoint).

- [ ] **Step 3: Viết code**

`realtime/SseEmitterSink.java`:
```java
package com.banhmyking.banhmyking.realtime;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/** RealtimeSink thật: ghi tin SSE (data JSON) ra SseEmitter. */
public class SseEmitterSink implements RealtimeSink {

    private final SseEmitter emitter;

    public SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
    }

    @Override
    public void sendComment(String comment) throws IOException {
        emitter.send(SseEmitter.event().comment(comment));
    }

    @Override
    public void close() {
        emitter.complete();
    }
}
```

`realtime/RealtimeEventListener.java`:
```java
package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Chỉ đẩy tin SAU KHI giao dịch đã lưu — trình duyệt gọi lại API sẽ thấy dữ liệu mới;
 * giao dịch rollback thì không gửi gì. fallbackExecution: sự kiện phát ngoài giao dịch vẫn được gửi.
 */
@Component
@RequiredArgsConstructor
public class RealtimeEventListener {

    private final RealtimeHub hub;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderChanged(OrderChangedEvent event) {
        hub.publish(event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInboxChanged(InboxChangedEvent event) {
        hub.publish(event);
    }
}
```

`controller/RealtimeController.java`:
```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.realtime.RealtimeHub;
import com.banhmyking.banhmyking.realtime.RealtimeUser;
import com.banhmyking.banhmyking.realtime.SseEmitterSink;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

/** Luồng tín hiệu realtime (spec realtime §4). Mọi vai trò đã đăng nhập đều mở được. */
@RestController
@RequestMapping("/api/v1/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final RealtimeHub hub;
    private final UserRepository userRepository;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails principal, HttpServletResponse response) {
        Long userId = SecurityUtils.requireUserId(principal);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        // Không cho proxy (Nginx) gom dữ liệu — tin phải tới ngay
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String connectionId = hub.register(RealtimeUser.from(user), new SseEmitterSink(emitter));
        emitter.onCompletion(() -> hub.unregister(connectionId));
        emitter.onTimeout(() -> {
            hub.unregister(connectionId);
            emitter.complete();
        });
        emitter.onError(ex -> hub.unregister(connectionId));
        return emitter;
    }
}
```

`config/SecurityConfig.java` — thêm import và dòng đầu tiên trong `authorizeHttpRequests`:
```java
import jakarta.servlet.DispatcherType;
```
```java
                .authorizeHttpRequests(auth -> auth
                        // SSE trả dữ liệu dần qua async dispatch; bộ lọc JWT không chạy lại ở bước này nên
                        // phải cho qua, nếu không luồng realtime lỗi AccessDenied khi kết thúc (spec realtime §4.3).
                        // Không mở ERROR dispatch để giữ nguyên cách trả lỗi 401/403.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
```
(Không cần quy tắc riêng cho `/api/v1/realtime/**` — đã nằm trong `"/api/v1/**").authenticated()`.)

- [ ] **Step 4: Chạy test, xác nhận qua**

Run: `./mvnw -q test -Dtest="RealtimeStreamSecurityTest,NewsCareersSecurityTest,StoreScopedEndpointsSecurityTest"`
Expected: PASS — test mới qua, hai test bảo mật cũ không bị ảnh hưởng.

- [ ] **Step 5: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 4: Phát `OrderChangedEvent` từ mọi đường đổi đơn

**Files:**
- Modify: `src/main/java/com/banhmyking/banhmyking/service/impl/OrderServiceImpl.java` (field ~dòng 40, `recordHistory` ~153, `createFromCart` ~362, `assignShipper` ~487-495, `rejectAssignedOrder` ~597-607, `transferStore` ~777-799)
- Modify: `src/main/java/com/banhmyking/banhmyking/service/impl/PaymentServiceImpl.java` (field ~47, `recordPaymentHistory` ~431)
- Modify tests: `service/OrderOwnershipTest.java`, `OrderPriceSnapshotTest.java`, `OrderServiceApisTest.java`, `OrderServiceStateMachineTest.java`, `OrderServiceTest.java`, `OrderStoreScopeTest.java`, `PaymentServiceTest.java`

**Interfaces:**
- Consumes: `OrderChangedEvent.of(...)`, `OrderChangeKind` (Task 1).
- Produces: `OrderServiceImpl` và `PaymentServiceImpl` có thêm dependency `ApplicationEventPublisher eventPublisher` (field `private final` **cuối cùng** trong nhóm final → tham số cuối của constructor Lombok). Mỗi lần ghi lịch sử đơn phát đúng một `OrderChangedEvent`.

- [ ] **Step 1: Thêm mock publisher cho các test hiện có (để code mới không làm NPE)**

Trong **mỗi** file `OrderOwnershipTest`, `OrderPriceSnapshotTest`, `OrderServiceApisTest`, `OrderServiceStateMachineTest`, `OrderServiceTest`, `OrderStoreScopeTest`, `PaymentServiceTest` thêm field cạnh các `@Mock` khác:
```java
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
```
Trong `OrderOwnershipTest.setUp()` (~dòng 102) thêm `eventPublisher` làm tham số cuối:
```java
        internalPaymentService = new PaymentServiceImpl(
                paymentRepository, orderRepository, userRepository, orderStatusHistoryRepository, inventoryService,
                storeAccessGuard, eventPublisher);
```

- [ ] **Step 2: Viết các assert hỏng vào test sẵn có**

Thêm import (nếu file chưa có) ở mỗi file được sửa dưới đây:
```java
import com.banhmyking.banhmyking.event.OrderChangeKind;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
```

(a) `OrderServiceTest.createFromCart_success` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderChangeKind.CREATED, event.getValue().kind());
```

(b) `OrderServiceStateMachineTest.updateOrderStatus_pendingToConfirmed_success` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderChangeKind.STATUS_CHANGED, event.getValue().kind());
        assertEquals(OrderStatus.PENDING, event.getValue().fromStatus());
        assertEquals(OrderStatus.CONFIRMED, event.getValue().toStatus());
```

(c) `OrderServiceStateMachineTest.cancelOrder_byCustomer_success` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderStatus.CANCELLED, event.getValue().toStatus());
```

(d) `OrderServiceStateMachineTest.cancelStalePendingOrders_cancelsAndReleases` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderStatus.CANCELLED, event.getValue().toStatus());
```

(e) `OrderServiceApisTest.assignShipper_whenValid_shouldAssignShipperAndLogHistory` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderChangeKind.SHIPPER_ASSIGNED, event.getValue().kind());
        assertEquals(4L, event.getValue().shipperId());
        assertNull(event.getValue().previousShipperId());
```

(f) `OrderServiceApisTest.confirmDelivery_whenAssignedShipper_shouldTransitionToDelivered` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderStatus.DELIVERED, event.getValue().toStatus());
```

(g) `OrderServiceApisTest` — thêm test mới (cạnh nhóm "6. Shipper"), import thêm `com.banhmyking.banhmyking.dto.order.RejectOrderRequest` nếu chưa có:
```java
    @Test
    @DisplayName("rejectAssignedOrder - gỡ shipper, ghi 1 dòng lịch sử và báo cả shipper vừa bị gỡ")
    void rejectAssignedOrder_publishesEventForPreviousShipper() {
        sampleOrder.setShipper(shipper);
        when(userRepository.findById(4L)).thenReturn(Optional.of(shipper));
        when(orderRepository.findByOrderCode("BMK-20260909-TEST1")).thenReturn(Optional.of(sampleOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.rejectAssignedOrder(4L, "BMK-20260909-TEST1",
                RejectOrderRequest.builder().reason("Xe hỏng").build());

        verify(orderStatusHistoryRepository, times(1)).save(any(OrderStatusHistory.class));
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderChangeKind.SHIPPER_ASSIGNED, event.getValue().kind());
        assertNull(event.getValue().shipperId());
        assertEquals(4L, event.getValue().previousShipperId());
    }
```

(h) `OrderStoreScopeTest.transferMovesPendingOrderAndRecomputesTotalWhenUnpaid` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderChangeKind.STORE_TRANSFERRED, event.getValue().kind());
        org.junit.jupiter.api.Assertions.assertNotNull(event.getValue().previousStoreId());
        org.junit.jupiter.api.Assertions.assertNotEquals(event.getValue().storeId(), event.getValue().previousStoreId());
```

(i) `PaymentServiceTest.processSepayWebhook_success` — cuối method:
```java
        ArgumentCaptor<OrderChangedEvent> event = ArgumentCaptor.forClass(OrderChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        assertEquals(OrderStatus.CONFIRMED, event.getValue().toStatus());
        assertEquals(OrderChangeKind.STATUS_CHANGED, event.getValue().kind());
```

- [ ] **Step 3: Chạy test, xác nhận hỏng**

Run: `./mvnw -q test -Dtest="OrderServiceTest,OrderServiceStateMachineTest,OrderServiceApisTest,OrderStoreScopeTest,PaymentServiceTest,OrderOwnershipTest,OrderPriceSnapshotTest"`
Expected: FAIL — `OrderOwnershipTest` lỗi biên dịch (constructor `PaymentServiceImpl` chưa nhận 7 tham số); sau khi sửa constructor ở Step 4 các assert `verify(eventPublisher...)` mới hỏng vì chưa phát sự kiện.

- [ ] **Step 4: Viết code**

`OrderServiceImpl` — thêm import:
```java
import com.banhmyking.banhmyking.event.OrderChangeKind;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
```
Thêm field ngay sau `private final com.banhmyking.banhmyking.security.StoreAccessGuard storeAccessGuard;` (là field `final` cuối cùng):
```java
    /** Phát OrderChangedEvent cho realtime — gửi đi sau khi commit (RealtimeEventListener) */
    private final ApplicationEventPublisher eventPublisher;
```
Thay toàn bộ hàm `recordHistory` hiện có bằng:
```java
    /** Ghi 1 dòng order_status_history — block lặp 4 lần, gom về đây. kind suy ra từ trạng thái. */
    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus,
                               User changedBy, String note) {
        recordHistory(order, fromStatus, toStatus, changedBy, note, null, null, null);
    }

    /**
     * Ghi 1 dòng lịch sử và phát OrderChangedEvent (spec realtime §2.2). Mọi đường đổi đơn đi qua đây
     * nên realtime không phải chèn code vào từng nghiệp vụ.
     */
    private void recordHistory(Order order, OrderStatus fromStatus, OrderStatus toStatus, User changedBy,
                               String note, OrderChangeKind kind, Long previousStoreId, Long previousShipperId) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrder(order);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setChangedBy(changedBy);
        history.setNote(note);
        orderStatusHistoryRepository.save(history);
        eventPublisher.publishEvent(OrderChangedEvent.of(order, fromStatus, toStatus, kind,
                previousStoreId, previousShipperId));
    }
```
`createFromCart` (~dòng 362):
```java
        recordHistory(order, OrderStatus.PENDING, OrderStatus.PENDING, user, "Đơn hàng được tạo",
                OrderChangeKind.CREATED, null, null);
```
`assignShipper` — ngay trước `order.setShipper(shipper);` thêm:
```java
        Long previousShipperId = order.getShipper() == null ? null : order.getShipper().getId();
```
và đổi dòng `recordHistory(updatedOrder, currentStatus, currentStatus, actor, historyNote);` thành:
```java
        recordHistory(updatedOrder, currentStatus, currentStatus, actor, historyNote,
                OrderChangeKind.SHIPPER_ASSIGNED, null, previousShipperId);
```
`rejectAssignedOrder` — xoá khối `// Ghi lịch sử từ chối trước khi gỡ gán` (từ `OrderStatusHistory history = new OrderStatusHistory();` tới `orderStatusHistoryRepository.save(history);`) và thay đoạn gỡ shipper bằng:
```java
        // Gỡ gán shipper để Staff phân công lại cho người khác; báo cả shipper vừa bị gỡ (previousShipperId)
        Long previousShipperId = order.getShipper().getId();
        order.setShipper(null);
        Order updatedOrder = orderRepository.save(order);
        recordHistory(updatedOrder, OrderStatus.READY_FOR_PICKUP, OrderStatus.READY_FOR_PICKUP, actor,
                "Tài xế " + actor.getFullName() + " (" + actor.getPhone() + ") từ chối nhận đơn: " + reason,
                OrderChangeKind.SHIPPER_ASSIGNED, null, previousShipperId);
```
`transferStore` — ngay trước `String fromName = ...` thêm:
```java
        Long previousStoreId = order.getStore() == null ? null : order.getStore().getId();
        Long previousShipperId = order.getShipper() == null ? null : order.getShipper().getId();
```
và đổi dòng cuối `recordHistory(saved, OrderStatus.PENDING, OrderStatus.PENDING, actor, note);` thành:
```java
        recordHistory(saved, OrderStatus.PENDING, OrderStatus.PENDING, actor, note,
                OrderChangeKind.STORE_TRANSFERRED, previousStoreId, previousShipperId);
```

`PaymentServiceImpl` — thêm import:
```java
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
```
Thêm field ngay sau `private final com.banhmyking.banhmyking.security.StoreAccessGuard storeAccessGuard;`:
```java
    /** Phát OrderChangedEvent khi tiền về đổi trạng thái đơn (realtime) */
    private final ApplicationEventPublisher eventPublisher;
```
Cuối hàm `recordPaymentHistory`, sau `orderStatusHistoryRepository.save(history);`:
```java
        eventPublisher.publishEvent(OrderChangedEvent.of(order, fromStatus, toStatus, null, null, null));
```

- [ ] **Step 5: Chạy toàn bộ test service + controller**

Run: `./mvnw -q test -Dtest="Order*Test,Payment*Test,*ControllerTest"`
Expected: PASS — các assert mới qua, test cũ không đổi kết quả.

- [ ] **Step 6: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 5: Phát `InboxChangedEvent` từ phản hồi và hồ sơ ứng tuyển

**Files:**
- Modify: `src/main/java/com/banhmyking/banhmyking/service/FeedbackService.java` (field ~60, `submit` ~119, `update` ~148)
- Modify: `src/main/java/com/banhmyking/banhmyking/service/JobApplicationService.java` (field ~57, `submit` ~115, `update` ~190)
- Modify tests: `src/test/java/com/banhmyking/banhmyking/service/FeedbackServiceTest.java`, `JobApplicationServiceTest.java`

**Interfaces:**
- Consumes: `InboxChangedEvent`, `InboxType` (Task 1).
- Produces: hai service có dependency `ApplicationEventPublisher eventPublisher` là field `final` **cuối cùng** (sau `clock`) → tham số cuối constructor.

- [ ] **Step 1: Cập nhật constructor trong test + viết assert hỏng**

Ở cả hai file test thêm:
```java
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;
```
`FeedbackServiceTest.setUp()` — thêm `eventPublisher` cuối danh sách tham số:
```java
        service = new FeedbackService(feedbackRepository, orderRepository, storeRepository, userRepository,
                new StoreAccessGuard(), new SubmissionRateLimiter(clock), siteSettingService, emailService, clock,
                eventPublisher);
```
`JobApplicationServiceTest.setUp()`:
```java
        service = new JobApplicationService(new JobPostingService(jobPostingRepository, storeRepository, clock),
                jobApplicationRepository, userRepository, new StoreAccessGuard(), cvStorageService,
                new SubmissionRateLimiter(clock), emailService, clock, eventPublisher);
```
Import thêm (nếu chưa có) ở cả hai file:
```java
import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.InboxType;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
```

`FeedbackServiceTest.ownOrderDecidesStoreAndClientStoreIsIgnored` — cuối method (so với cơ sở của phản hồi thực sự được lưu):
```java
        ArgumentCaptor<com.banhmyking.banhmyking.entity.Feedback> saved =
                ArgumentCaptor.forClass(com.banhmyking.banhmyking.entity.Feedback.class);
        verify(feedbackRepository).save(saved.capture());
        ArgumentCaptor<InboxChangedEvent> event = ArgumentCaptor.forClass(InboxChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        org.assertj.core.api.Assertions.assertThat(event.getValue())
                .isEqualTo(new InboxChangedEvent(InboxType.FEEDBACK, saved.getValue().getStore().getId()));
```
`FeedbackServiceTest.honeypotReturnsFalseWithoutSaving` — cuối method:
```java
        verify(eventPublisher, never()).publishEvent(any(InboxChangedEvent.class));
```
`FeedbackServiceTest.updateStatusRecordsHandler` — cuối method:
```java
        ArgumentCaptor<InboxChangedEvent> event = ArgumentCaptor.forClass(InboxChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        org.assertj.core.api.Assertions.assertThat(event.getValue().type()).isEqualTo(InboxType.FEEDBACK);
```
`JobApplicationServiceTest.successSavesNormalisedFieldsCvAndIpThenSendsConfirmation` — cuối method:
```java
        ArgumentCaptor<com.banhmyking.banhmyking.entity.JobApplication> saved =
                ArgumentCaptor.forClass(com.banhmyking.banhmyking.entity.JobApplication.class);
        verify(jobApplicationRepository).save(saved.capture());
        ArgumentCaptor<InboxChangedEvent> event = ArgumentCaptor.forClass(InboxChangedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(event.capture());
        org.assertj.core.api.Assertions.assertThat(event.getValue())
                .isEqualTo(new InboxChangedEvent(InboxType.JOB_APPLICATION, saved.getValue().getStore().getId()));
```
`JobApplicationServiceTest.honeypotReturnsFalseWithoutTouchingAnything` — cuối method:
```java
        verify(eventPublisher, never()).publishEvent(any(InboxChangedEvent.class));
```

- [ ] **Step 2: Chạy test, xác nhận hỏng**

Run: `./mvnw -q test -Dtest="FeedbackServiceTest,JobApplicationServiceTest"`
Expected: FAIL — lỗi biên dịch constructor (chưa nhận `eventPublisher`).

- [ ] **Step 3: Viết code**

Cả hai service thêm import:
```java
import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.InboxType;
import org.springframework.context.ApplicationEventPublisher;
```
và field sau `private final Clock clock;`:
```java
    /** Báo huy hiệu hộp thư của admin/manager cập nhật ngay (realtime) */
    private final ApplicationEventPublisher eventPublisher;
```

`FeedbackService.submit` — ngay sau `feedbackRepository.save(feedback);`:
```java
        eventPublisher.publishEvent(new InboxChangedEvent(InboxType.FEEDBACK, store == null ? null : store.getId()));
```
`FeedbackService.update` — trong khối `if (request.status() != null && request.status() != feedback.getStatus()) {`, sau `feedback.setStatus(request.status());`:
```java
            // Số "mới" trên huy hiệu của người khác vừa đổi
            eventPublisher.publishEvent(new InboxChangedEvent(InboxType.FEEDBACK,
                    feedback.getStore() == null ? null : feedback.getStore().getId()));
```

`JobApplicationService.submit` — ngay sau khối `try { jobApplicationRepository.save(application); } catch (...) {...}`:
```java
        eventPublisher.publishEvent(new InboxChangedEvent(InboxType.JOB_APPLICATION, store.getId()));
```
`JobApplicationService.update` — trong khối `if (request.status() != null && request.status() != app.getStatus()) {`, sau `app.setStatus(request.status());`:
```java
            eventPublisher.publishEvent(new InboxChangedEvent(InboxType.JOB_APPLICATION,
                    app.getStore() == null ? null : app.getStore().getId()));
```

- [ ] **Step 4: Chạy toàn bộ test backend**

Run: `./mvnw -q test`
Expected: PASS — toàn bộ test (kể cả `NewsCareersSecurityTest` dùng hai service này qua Spring context).

- [ ] **Step 5: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 6: Vitest + các hàm thuần phía frontend

**Files:**
- Modify: `frontend/package.json` (scripts + devDependencies)
- Create: `frontend/src/realtime/types.ts`
- Create: `frontend/src/realtime/sseParser.ts`, `frontend/src/realtime/sseParser.test.ts`
- Create: `frontend/src/realtime/backoff.ts`, `frontend/src/realtime/backoff.test.ts`
- Create: `frontend/src/realtime/coalescer.ts`, `frontend/src/realtime/coalescer.test.ts`

**Interfaces:**
- Produces:
  - `types.ts`: `RealtimeStatus = 'live' | 'reconnecting' | 'offline'`; `OrderChangeKind`; `OrderSignal { orderCode: string; status: OrderStatus; kind: OrderChangeKind; storeId: number | null; shipperId: number | null }`; `InboxSignal { type: 'FEEDBACK' | 'JOB_APPLICATION' }`; `RealtimeMessage = { type: 'ready' } | { type: 'resync' } | { type: 'order'; payload: OrderSignal } | { type: 'inbox'; payload: InboxSignal }`.
  - `createSseParser(): (chunk: string) => SseMessage[]`, `SseMessage { event: string; data: string }`.
  - `backoffDelay(attempt: number, random?: () => number): number`, `STABLE_AFTER_MS = 60_000`.
  - `createCoalescer(fn: () => void, waitMs: number): { trigger(): void; cancel(): void }`.

- [ ] **Step 1: Cài Vitest và thêm script**

Run (trong `frontend/`): `npm install --save-dev vitest`
Sửa `frontend/package.json` mục `scripts`, thêm:
```json
    "test": "vitest run",
```

- [ ] **Step 2: Viết test hỏng**

`frontend/src/realtime/sseParser.test.ts`:
```ts
import { describe, expect, it } from 'vitest';
import { createSseParser } from './sseParser';

describe('createSseParser', () => {
  it('đọc một tin đầy đủ theo định dạng Spring (không có dấu cách sau dấu hai chấm)', () => {
    const parse = createSseParser();
    expect(parse('event:order\ndata:{"orderCode":"A"}\n\n')).toEqual([{ event: 'order', data: '{"orderCode":"A"}' }]);
  });

  it('ghép tin bị cắt giữa hai khúc', () => {
    const parse = createSseParser();
    expect(parse('event:ord')).toEqual([]);
    expect(parse('er\ndata:{"a":1}')).toEqual([]);
    expect(parse('\n\n')).toEqual([{ event: 'order', data: '{"a":1}' }]);
  });

  it('tách nhiều tin trong một khúc', () => {
    const parse = createSseParser();
    expect(parse('event:ready\ndata:{}\n\nevent:inbox\ndata:{"type":"FEEDBACK"}\n\n')).toEqual([
      { event: 'ready', data: '{}' },
      { event: 'inbox', data: '{"type":"FEEDBACK"}' },
    ]);
  });

  it('bỏ qua dòng chú thích ping', () => {
    const parse = createSseParser();
    expect(parse(':ping\n\n')).toEqual([]);
  });

  it('hiểu \\r\\n, kể cả khi \\r và \\n rơi vào hai khúc khác nhau', () => {
    const parse = createSseParser();
    expect(parse('event: order\r\ndata: x\r')).toEqual([]);
    expect(parse('\n\r\n')).toEqual([{ event: 'order', data: 'x' }]);
  });

  it('nối nhiều dòng data bằng xuống dòng và mặc định tên tin là message', () => {
    const parse = createSseParser();
    expect(parse('data:a\ndata:b\n\n')).toEqual([{ event: 'message', data: 'a\nb' }]);
  });
});
```

`frontend/src/realtime/backoff.test.ts`:
```ts
import { describe, expect, it } from 'vitest';
import { backoffDelay } from './backoff';

const noJitter = () => 0.5; // 0.5 → hệ số 1.0

describe('backoffDelay', () => {
  it('theo lịch 1s → 2s → 5s → 10s → 30s', () => {
    expect([0, 1, 2, 3, 4].map((attempt) => backoffDelay(attempt, noJitter))).toEqual([1000, 2000, 5000, 10000, 30000]);
  });

  it('giữ 30s cho mọi lần sau', () => {
    expect(backoffDelay(9, noJitter)).toBe(30000);
  });

  it('jitter nằm trong ±20%', () => {
    expect(backoffDelay(0, () => 0)).toBe(800);
    expect(backoffDelay(0, () => 1)).toBe(1200);
  });
});
```

`frontend/src/realtime/coalescer.test.ts`:
```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createCoalescer } from './coalescer';

describe('createCoalescer', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('gộp nhiều lần trigger trong khoảng chờ thành một lần chạy', () => {
    const fn = vi.fn();
    const coalescer = createCoalescer(fn, 300);
    coalescer.trigger();
    coalescer.trigger();
    vi.advanceTimersByTime(299);
    coalescer.trigger();
    expect(fn).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it('trigger sau khi đã chạy thì mở lượt mới', () => {
    const fn = vi.fn();
    const coalescer = createCoalescer(fn, 300);
    coalescer.trigger();
    vi.advanceTimersByTime(300);
    coalescer.trigger();
    vi.advanceTimersByTime(300);
    expect(fn).toHaveBeenCalledTimes(2);
  });

  it('cancel huỷ lượt đang chờ', () => {
    const fn = vi.fn();
    const coalescer = createCoalescer(fn, 300);
    coalescer.trigger();
    coalescer.cancel();
    vi.advanceTimersByTime(1000);
    expect(fn).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 3: Chạy test, xác nhận hỏng**

Run (trong `frontend/`): `npm run test`
Expected: FAIL — `Failed to resolve import "./sseParser"` (và tương tự cho `backoff`, `coalescer`).

- [ ] **Step 4: Viết code**

`frontend/src/realtime/types.ts`:
```ts
import type { OrderStatus } from '../types/order';

export type RealtimeStatus = 'live' | 'reconnecting' | 'offline';

export type OrderChangeKind = 'CREATED' | 'STATUS_CHANGED' | 'SHIPPER_ASSIGNED' | 'STORE_TRANSFERRED' | 'UPDATED';

/** Tín hiệu "đơn vừa đổi" — trang tự gọi lại API để lấy dữ liệu thật */
export interface OrderSignal {
  orderCode: string;
  status: OrderStatus;
  kind: OrderChangeKind;
  storeId: number | null;
  shipperId: number | null;
}

export interface InboxSignal {
  type: 'FEEDBACK' | 'JOB_APPLICATION';
}

/** `resync` không đến từ server: provider phát khi nối lại được, để trang tải bù những gì đã lỡ */
export type RealtimeMessage =
  | { type: 'ready' }
  | { type: 'resync' }
  | { type: 'order'; payload: OrderSignal }
  | { type: 'inbox'; payload: InboxSignal };
```

`frontend/src/realtime/sseParser.ts`:
```ts
export interface SseMessage {
  event: string;
  data: string;
}

const parseBlock = (block: string): SseMessage | null => {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line === '' || line.startsWith(':')) continue;
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    if (field === 'event') event = value;
    else if (field === 'data') data.push(value);
  }
  return data.length === 0 ? null : { event, data: data.join('\n') };
};

/**
 * Bộ đọc SSE tăng dần: nhận từng khúc văn bản từ luồng fetch (có thể cắt giữa chừng)
 * và trả các tin đã nhận đủ. Dòng chú thích (`: ping`) bị bỏ qua.
 */
export const createSseParser = () => {
  let buffer = '';

  return (chunk: string): SseMessage[] => {
    buffer += chunk;
    // '\r' ở cuối khúc có thể là nửa đầu của '\r\n' — giữ lại chờ khúc sau
    const heldBack = buffer.endsWith('\r') ? '\r' : '';
    let text = (heldBack ? buffer.slice(0, -1) : buffer).replace(/\r\n?/g, '\n');

    const messages: SseMessage[] = [];
    let boundary = text.indexOf('\n\n');
    while (boundary !== -1) {
      const message = parseBlock(text.slice(0, boundary));
      if (message) messages.push(message);
      text = text.slice(boundary + 2);
      boundary = text.indexOf('\n\n');
    }

    buffer = text + heldBack;
    return messages;
  };
};
```

`frontend/src/realtime/backoff.ts`:
```ts
const STEPS_MS = [1000, 2000, 5000, 10000, 30000];

/** Giữ được kết nối lâu hơn ngần này thì lần mất kết nối sau bắt đầu lại từ 1 giây */
export const STABLE_AFTER_MS = 60_000;

/**
 * Thời gian chờ trước lần nối lại thứ `attempt` (0 = lần đầu thất bại), cộng ngẫu nhiên ±20%
 * để nhiều tab không cùng dội vào server một lúc khi backend khởi động lại.
 */
export const backoffDelay = (attempt: number, random: () => number = Math.random): number => {
  const base = STEPS_MS[Math.min(Math.max(attempt, 0), STEPS_MS.length - 1)];
  return Math.round(base * (0.8 + random() * 0.4));
};
```

`frontend/src/realtime/coalescer.ts`:
```ts
/** Gộp nhiều lần `trigger` trong `waitMs` thành một lần chạy `fn` — tránh tải lại dồn dập khi tin đến liền nhau */
export const createCoalescer = (fn: () => void, waitMs: number) => {
  let timer: ReturnType<typeof setTimeout> | null = null;

  return {
    trigger() {
      if (timer !== null) return;
      timer = setTimeout(() => {
        timer = null;
        fn();
      }, waitMs);
    },
    cancel() {
      if (timer === null) return;
      clearTimeout(timer);
      timer = null;
    },
  };
};
```

- [ ] **Step 5: Chạy test, typecheck, lint**

Run (trong `frontend/`): `npm run test && npx tsc -p tsconfig.app.json --noEmit && npx oxlint src/realtime`
Expected: 12 test PASS; tsc không lỗi; oxlint 0 lỗi.

- [ ] **Step 6: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 7: Client luồng + `RealtimeProvider` + `useLiveRefresh`

**Files:**
- Create: `frontend/src/realtime/realtimeClient.ts`
- Create: `frontend/src/context/realtimeContextDef.ts`
- Create: `frontend/src/context/RealtimeProvider.tsx`
- Create: `frontend/src/context/useRealtime.ts`
- Create: `frontend/src/hooks/useLiveRefresh.ts`
- Modify: `frontend/src/App.tsx` (bọc provider, khoảng dòng 84-86)

**Interfaces:**
- Consumes: `createSseParser`, `backoffDelay`, `STABLE_AFTER_MS`, `createCoalescer`, các kiểu trong `realtime/types.ts` (Task 6); `authApi.getMe()`; `tokenStorage.getAccessToken()`; `useAuth()`; `orderSyncChannel` (`utils/orderSyncChannel.ts`).
- Produces:
  - `startRealtimeClient({ onMessage, onStatus }): () => void` (hàm dừng).
  - `RealtimeContext` với `{ status: RealtimeStatus; subscribe(listener: (m: RealtimeMessage) => void): () => void }`.
  - `useRealtime(listener)`, `useRealtimeStatus(): RealtimeStatus`.
  - `useLiveRefresh(refresh: () => void | Promise<void>, options?: { matchOrder?: (s: OrderSignal) => boolean; inbox?: boolean; fallbackMs?: number; enabled?: boolean })`.

- [ ] **Step 1: Viết `realtimeClient.ts`**

```ts
import { authApi } from '../api/authApi';
import { tokenStorage } from '../utils/tokenStorage';
import { backoffDelay, STABLE_AFTER_MS } from './backoff';
import { createSseParser, type SseMessage } from './sseParser';
import type { RealtimeMessage, RealtimeStatus } from './types';

const STREAM_URL = `${import.meta.env.VITE_API_BASE_URL || '/api/v1'}/realtime/stream`;

export interface RealtimeClientOptions {
  onMessage: (message: RealtimeMessage) => void;
  onStatus: (status: RealtimeStatus) => void;
}

const toMessage = ({ event, data }: SseMessage): RealtimeMessage | null => {
  if (event === 'ready') return { type: 'ready' };
  if (event !== 'order' && event !== 'inbox') return null;
  try {
    return { type: event, payload: JSON.parse(data) } as RealtimeMessage;
  } catch {
    return null;
  }
};

/**
 * Mở luồng SSE bằng fetch (EventSource không gửi được header Authorization), tự nối lại theo backoff.
 * 401: để interceptor axios làm mới token qua getMe() rồi nối lại; làm mới hỏng thì dừng — luồng đăng
 * xuất sẵn có lo phần còn lại. Trả hàm dừng (gọi khi đăng xuất / unmount).
 */
export const startRealtimeClient = ({ onMessage, onStatus }: RealtimeClientOptions): (() => void) => {
  let stopped = false;
  let attempt = 0;
  let controller: AbortController | null = null;
  let retryTimer: number | null = null;

  const scheduleRetry = () => {
    if (stopped) return;
    onStatus('reconnecting');
    retryTimer = window.setTimeout(() => void connect(), backoffDelay(attempt));
    attempt += 1;
  };

  const connect = async () => {
    if (stopped) return;
    const token = tokenStorage.getAccessToken();
    if (!token) {
      onStatus('offline');
      return;
    }

    controller = new AbortController();
    const openedAt = Date.now();
    try {
      const response = await fetch(STREAM_URL, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        signal: controller.signal,
        cache: 'no-store',
      });

      if (response.status === 401) {
        try {
          await authApi.getMe();
        } catch {
          onStatus('offline');
          return;
        }
        scheduleRetry();
        return;
      }
      if (!response.ok || !response.body) {
        scheduleRetry();
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      const parse = createSseParser();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        for (const raw of parse(decoder.decode(value, { stream: true }))) {
          const message = toMessage(raw);
          if (!message) continue;
          if (message.type === 'ready') onStatus('live');
          onMessage(message);
        }
      }
    } catch {
      // abort khi dừng, hoặc mất mạng giữa chừng — xử lý chung bên dưới
    }

    if (stopped) return;
    if (Date.now() - openedAt >= STABLE_AFTER_MS) attempt = 0;
    scheduleRetry();
  };

  void connect();

  return () => {
    stopped = true;
    controller?.abort();
    if (retryTimer !== null) window.clearTimeout(retryTimer);
  };
};
```

- [ ] **Step 2: Viết context + provider + hook**

`frontend/src/context/realtimeContextDef.ts`:
```ts
import { createContext } from 'react';
import type { RealtimeMessage, RealtimeStatus } from '../realtime/types';

export type RealtimeListener = (message: RealtimeMessage) => void;

export interface RealtimeContextType {
  status: RealtimeStatus;
  subscribe: (listener: RealtimeListener) => () => void;
}

/** Mặc định (ngoài provider): ngoại tuyến, không phát gì — trang tự rơi về polling dự phòng */
export const RealtimeContext = createContext<RealtimeContextType>({
  status: 'offline',
  subscribe: () => () => {},
});
```

`frontend/src/context/RealtimeProvider.tsx`:
```tsx
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { startRealtimeClient } from '../realtime/realtimeClient';
import type { RealtimeStatus } from '../realtime/types';
import { RealtimeContext, type RealtimeListener } from './realtimeContextDef';
import { useAuth } from './useAuth';

/**
 * Một luồng realtime cho cả app (spec realtime §5.2): mở khi đã đăng nhập, đóng khi đăng xuất.
 * Nhận lại `ready` sau khi mất kết nối → phát `resync` để mọi trang đang mở tải bù một lần.
 */
export const RealtimeProvider = ({ children }: { children: ReactNode }) => {
  const { isAuthenticated } = useAuth();
  const [status, setStatus] = useState<RealtimeStatus>('offline');
  const listenersRef = useRef(new Set<RealtimeListener>());

  const subscribe = useCallback((listener: RealtimeListener) => {
    listenersRef.current.add(listener);
    return () => {
      listenersRef.current.delete(listener);
    };
  }, []);

  useEffect(() => {
    if (!isAuthenticated) return;
    let readyCount = 0;
    const emit: RealtimeListener = (message) => {
      listenersRef.current.forEach((listener) => listener(message));
    };

    return startRealtimeClient({
      onStatus: setStatus,
      onMessage: (message) => {
        if (message.type === 'ready') {
          readyCount += 1;
          if (readyCount > 1) emit({ type: 'resync' });
          return;
        }
        emit(message);
      },
    });
  }, [isAuthenticated]);

  const value = useMemo(
    () => ({ status: isAuthenticated ? status : ('offline' as const), subscribe }),
    [isAuthenticated, status, subscribe]
  );

  return <RealtimeContext.Provider value={value}>{children}</RealtimeContext.Provider>;
};
```

`frontend/src/context/useRealtime.ts`:
```ts
import { useContext, useEffect, useRef } from 'react';
import type { RealtimeStatus } from '../realtime/types';
import { RealtimeContext, type RealtimeListener } from './realtimeContextDef';

export const useRealtimeStatus = (): RealtimeStatus => useContext(RealtimeContext).status;

/** Nghe mọi tin realtime. `listener` đọc qua ref nên truyền hàm inline thoải mái. */
export const useRealtime = (listener: RealtimeListener) => {
  const { subscribe } = useContext(RealtimeContext);
  const listenerRef = useRef(listener);
  useEffect(() => {
    listenerRef.current = listener;
  });
  useEffect(() => subscribe((message) => listenerRef.current(message)), [subscribe]);
};
```

- [ ] **Step 3: Viết `useLiveRefresh.ts`**

```ts
import { useEffect, useRef } from 'react';
import { useRealtime, useRealtimeStatus } from '../context/useRealtime';
import { createCoalescer } from '../realtime/coalescer';
import type { OrderSignal } from '../realtime/types';
import { orderSyncChannel } from '../utils/orderSyncChannel';

const COALESCE_MS = 300;

interface LiveRefreshOptions {
  /** Tin đơn nào làm trang tải lại; bỏ trống = mọi tin đơn hub đã gửi cho người này */
  matchOrder?: (signal: OrderSignal) => boolean;
  /** true = nghe tin hộp thư thay vì tin đơn */
  inbox?: boolean;
  /** Chu kỳ polling dự phòng, chỉ chạy khi luồng realtime không "live" */
  fallbackMs?: number;
  enabled?: boolean;
}

/**
 * Thay `usePolling` (spec realtime §5.2): tải lại khi có tín hiệu realtime khớp, khi nối lại (`resync`),
 * khi quay lại tab / focus, khi tab khác cùng trình duyệt báo qua BroadcastChannel; polling `fallbackMs`
 * chỉ khi mất luồng. Mọi nguồn đi qua một bộ gộp 300ms.
 */
export const useLiveRefresh = (
  refresh: () => void | Promise<void>,
  { matchOrder, inbox = false, fallbackMs = 30_000, enabled = true }: LiveRefreshOptions = {}
) => {
  const status = useRealtimeStatus();
  const refreshRef = useRef(refresh);
  const matchRef = useRef(matchOrder);
  const inboxRef = useRef(inbox);
  useEffect(() => {
    refreshRef.current = refresh;
    matchRef.current = matchOrder;
    inboxRef.current = inbox;
  });

  const coalescerRef = useRef<ReturnType<typeof createCoalescer> | null>(null);
  useEffect(() => {
    if (!enabled) return;
    const coalescer = createCoalescer(() => void refreshRef.current(), COALESCE_MS);
    coalescerRef.current = coalescer;
    return () => {
      coalescer.cancel();
      coalescerRef.current = null;
    };
  }, [enabled]);

  useRealtime((message) => {
    const coalescer = coalescerRef.current;
    if (!coalescer) return;
    if (message.type === 'resync') coalescer.trigger();
    else if (message.type === 'inbox' && inboxRef.current) coalescer.trigger();
    else if (message.type === 'order' && !inboxRef.current && (!matchRef.current || matchRef.current(message.payload))) {
      coalescer.trigger();
    }
  });

  useEffect(() => {
    if (!enabled) return;
    const run = () => coalescerRef.current?.trigger();
    const handleVisibility = () => {
      if (document.visibilityState === 'visible') run();
    };
    document.addEventListener('visibilitychange', handleVisibility);
    window.addEventListener('focus', run);
    orderSyncChannel?.addEventListener('message', run);
    // Polling dự phòng chỉ khi mất luồng realtime; tab ẩn thì bỏ lượt (quay lại tab sẽ tải ngay)
    const intervalId =
      status === 'live'
        ? null
        : window.setInterval(() => {
            if (document.visibilityState === 'visible') run();
          }, fallbackMs);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      window.removeEventListener('focus', run);
      orderSyncChannel?.removeEventListener('message', run);
      if (intervalId !== null) window.clearInterval(intervalId);
    };
  }, [enabled, status, fallbackMs]);
};
```

- [ ] **Step 4: Gắn provider vào `App.tsx`**

Thêm import:
```tsx
import { RealtimeProvider } from './context/RealtimeProvider';
```
Bọc ngay bên trong `<AuthProvider>` (provider cần `useAuth`):
```tsx
export const App: FC = () => (
  <SiteSettingsProvider>
    <AuthProvider>
    <RealtimeProvider>
    <StoreScopeProvider>
```
và đóng tương ứng ngay trước `</AuthProvider>`:
```tsx
    </StoreScopeProvider>
    </RealtimeProvider>
    </AuthProvider>
```

- [ ] **Step 5: Typecheck + lint + build**

Run (trong `frontend/`): `npx tsc -p tsconfig.app.json --noEmit && npx oxlint src/realtime src/context src/hooks && npm run build`
Expected: không lỗi (cảnh báo cũ của file khác được phép, không thêm cảnh báo mới ở file vừa tạo).

- [ ] **Step 6: Kiểm tra nhanh trong trình duyệt**

Chạy backend (8080) + `npm run dev`, đăng nhập, mở DevTools → Network: có request `realtime/stream` trạng thái 200, kiểu `eventsource`/`fetch` đang treo, tab EventStream (hoặc Response) thấy `event:ready`. Đợi 25 giây thấy `:ping`.

- [ ] **Step 7: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 8: Thay polling ở các màn

**Files:**
- Modify: `frontend/src/pages/staff/StaffOrderQueuePage.tsx` (import ~42, hằng ~47, effect ~97-118)
- Modify: `frontend/src/pages/shipper/ShipperOrdersPage.tsx` (import ~34, hằng ~39, effect ~130-149)
- Modify: `frontend/src/pages/OrderTrackingPage.tsx` (import ~11, hằng ~34, dòng ~103)
- Modify: `frontend/src/pages/OrdersPage.tsx` (import ~7, hằng ~16, lời gọi `usePolling` ~88-99)
- Modify: `frontend/src/pages/admin/AdminOrdersPage.tsx` (sau effect tải ~78-81)
- Modify: `frontend/src/hooks/useInboxCounts.ts`
- Delete: `frontend/src/hooks/usePolling.ts`

**Interfaces:**
- Consumes: `useLiveRefresh` (Task 7).

- [ ] **Step 1: `StaffOrderQueuePage`**

Thêm import `import { useLiveRefresh } from '../../hooks/useLiveRefresh';`, đổi import `orderSyncChannel` thành chỉ còn `import { broadcastOrderChange } from '../../utils/orderSyncChannel';`, xoá hằng `POLL_MS`. Thay nguyên effect polling (từ `useEffect(() => {\n    if (needsStore) return;\n    void fetchOrders();` tới hết `}, [fetchOrders, needsStore]);`) bằng:
```tsx
  useEffect(() => {
    if (needsStore) return;
    void fetchOrders();
  }, [fetchOrders, needsStore]);

  // Realtime: chỉ tải lại khi đơn thuộc cơ sở đang xem đổi (admin xem từng cơ sở một)
  useLiveRefresh(() => fetchOrders(), {
    enabled: !needsStore,
    matchOrder: (signal) => storeId == null || signal.storeId === storeId,
  });
```

- [ ] **Step 2: `ShipperOrdersPage`**

Thêm import `import { useLiveRefresh } from '../../hooks/useLiveRefresh';`, đổi import thành `import { broadcastOrderChange, playNotificationSound } from '../../utils/orderSyncChannel';`, xoá hằng `POLL_MS`. Thay nguyên effect (từ `useEffect(() => {\n    void fetchOrders();\n\n    const intervalId` tới `}, [fetchOrders]);`) bằng:
```tsx
  useEffect(() => {
    void fetchOrders();
  }, [fetchOrders]);

  // Hub chỉ gửi đơn của chính shipper này; logic so danh sách + chuông "đơn mới" có sẵn trong fetchOrders
  useLiveRefresh(() => fetchOrders(true));
```

- [ ] **Step 3: `OrderTrackingPage`**

Đổi import `usePolling` thành `import { useLiveRefresh } from '../hooks/useLiveRefresh';`, xoá hằng `POLL_MS = 5000`, thay dòng `usePolling(() => load(true), { intervalMs: POLL_MS, enabled: order != null && !isFinished });` và comment phía trên bằng:
```tsx
  // Đơn chưa kết thúc thì tự cập nhật: tức thì qua realtime, dự phòng 30s khi mất luồng
  const isFinished = order != null && TERMINAL.includes(order.status);
  useLiveRefresh(() => load(true), {
    enabled: order != null && !isFinished,
    matchOrder: (signal) => signal.orderCode === orderCode,
  });
```
(Giữ dòng `const isFinished = ...` chỉ một lần — dòng này đã có sẵn ngay trên lời gọi cũ.)

- [ ] **Step 4: `OrdersPage`**

Đổi import `usePolling` thành `import { useLiveRefresh } from '../hooks/useLiveRefresh';`, xoá hằng `LIST_POLL_MS`. Ở lời gọi hiện có đổi tên hàm `usePolling(` thành `useLiveRefresh(` và đối số thứ hai `{ intervalMs: LIST_POLL_MS, enabled: !isLoading && !error }` thành `{ enabled: !isLoading && !error }`. Sửa comment phía trên thành:
```tsx
  // Tự làm mới khi staff/shipper đổi trạng thái (realtime, dự phòng 30s). Kết quả của bộ lọc/trang cũ
  // (người dùng vừa bấm đổi giữa chừng) bị bỏ qua để không ghi đè danh sách đang xem.
```

- [ ] **Step 5: `AdminOrdersPage`**

Thêm import `import { useLiveRefresh } from '../../hooks/useLiveRefresh';` và ngay sau effect `useEffect(() => { setIsLoading(true); void load(); }, [load]);` thêm:
```tsx
  // Đơn đổi ở bếp / shipper / khách → bảng tự cập nhật, không cần bấm "Làm mới"
  useLiveRefresh(() => load(), {
    matchOrder: (signal) => storeId == null || signal.storeId === storeId,
  });
```

- [ ] **Step 6: `useInboxCounts`**

Thay import `usePolling` bằng `import { useLiveRefresh } from './useLiveRefresh';`, đổi hằng `const POLL_MS = 60_000;` thành `const FALLBACK_MS = 60_000;`, và thay dòng `usePolling(refresh, { intervalMs: POLL_MS, enabled });` bằng:
```ts
  // Có phản hồi / hồ sơ mới → huy hiệu cập nhật ngay; mất luồng thì hỏi lại mỗi 60s như trước
  useLiveRefresh(refresh, { inbox: true, enabled, fallbackMs: FALLBACK_MS });
```

- [ ] **Step 7: Xoá `usePolling`**

Run (trong `frontend/`): `rm src/hooks/usePolling.ts` — rồi kiểm tra không còn ai import: `grep -rn "usePolling" src` → không có kết quả. (Không dùng `git rm` — người dùng tự xử lý git.)

- [ ] **Step 8: Typecheck + lint + build**

Run (trong `frontend/`): `npx tsc -p tsconfig.app.json --noEmit && npx oxlint src && npm run build`
Expected: không lỗi; không thêm cảnh báo mới.

- [ ] **Step 9: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 9: Âm báo, nháy tab, chỉ báo kết nối thật

**Files:**
- Create: `frontend/src/utils/alertSound.ts`
- Modify: `frontend/src/utils/orderSyncChannel.ts` (hàm `playNotificationSound`)
- Create: `frontend/src/hooks/useOrderAlerts.ts`
- Create: `frontend/src/components/layout/AlertSoundToggle.tsx`
- Modify: `frontend/src/components/layout/DashboardLayout.tsx`
- Modify: `frontend/src/styles/layout.css` (thêm khối `.dash__sound`)
- Modify: `frontend/src/components/ui/PageHeader.tsx`
- Modify: `frontend/src/styles/components/page-header.css`

**Interfaces:**
- Consumes: `useRealtime`, `useRealtimeStatus` (Task 7); `useAuth`, `useStoreScope`; kiểu `RealtimeStatus`.
- Produces: `playAlertSound()`, `unlockAlertSound(): Promise<boolean>`, `isAlertSoundEnabled()`, `setAlertSoundEnabled(on)`; `useOrderAlerts({ role, userId, storeId })`; `<AlertSoundToggle />`.

- [ ] **Step 1: `utils/alertSound.ts`**

```ts
const PREF_KEY = 'bmk_alert_sound';

let sharedContext: AudioContext | null = null;

const getContext = (): AudioContext | null => {
  if (sharedContext) return sharedContext;
  const AudioContextClass =
    window.AudioContext || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!AudioContextClass) return null;
  sharedContext = new AudioContextClass();
  return sharedContext;
};

export const isAlertSoundEnabled = (): boolean => {
  try {
    return localStorage.getItem(PREF_KEY) !== 'off';
  } catch {
    return true;
  }
};

export const setAlertSoundEnabled = (enabled: boolean) => {
  try {
    localStorage.setItem(PREF_KEY, enabled ? 'on' : 'off');
  } catch {
    // Không lưu được (chế độ riêng tư) — lựa chọn chỉ có hiệu lực trong phiên này
  }
};

/** Gọi trong sự kiện bấm: trình duyệt chỉ cho phát âm thanh sau khi người dùng tương tác */
export const unlockAlertSound = async (): Promise<boolean> => {
  const ctx = getContext();
  if (!ctx) return false;
  try {
    await ctx.resume();
  } catch {
    // Bị chặn — trả về trạng thái hiện tại
  }
  return ctx.state === 'running';
};

const playTones = (ctx: AudioContext) => {
  const now = ctx.currentTime;
  const note = (frequency: number, start: number, end: number, volume: number) => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.type = 'sine';
    osc.frequency.setValueAtTime(frequency, now + start);
    gain.gain.setValueAtTime(volume, now + start);
    gain.gain.exponentialRampToValueAtTime(0.001, now + end);
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(now + start);
    osc.stop(now + end);
  };
  // "Ting" hai nốt E5 → G5
  note(659.25, 0, 0.2, 0.2);
  note(783.99, 0.12, 0.38, 0.25);
};

/** Tiếng "ting" dùng chung một AudioContext; tắt âm báo hoặc trình duyệt chặn thì im lặng */
export const playAlertSound = () => {
  if (!isAlertSoundEnabled()) return;
  const ctx = getContext();
  if (!ctx) return;
  try {
    if (ctx.state === 'running') playTones(ctx);
    else void ctx.resume().then(() => playTones(ctx)).catch(() => {});
  } catch {
    // Không bao giờ làm hỏng trang vì âm thanh
  }
};
```

- [ ] **Step 2: `orderSyncChannel.ts` dùng âm báo chung**

Thêm import đầu file `import { playAlertSound } from './alertSound';` và thay **toàn bộ thân** hàm `playNotificationSound` (giữ comment + tên export để `ShipperOrdersPage` không phải sửa):
```ts
/**
 * Phát chuông báo (Web Audio API, không file ngoài) qua AudioContext dùng chung của alertSound —
 * tôn trọng nút bật/tắt âm báo trong khu vận hành.
 */
export const playNotificationSound = () => {
  playAlertSound();
};
```

- [ ] **Step 3: `hooks/useOrderAlerts.ts`**

```ts
import { useEffect, useRef } from 'react';
import { useRealtime } from '../context/useRealtime';
import type { RoleName } from '../types/auth';
import { playAlertSound } from '../utils/alertSound';

interface OrderAlertsOptions {
  role?: RoleName;
  userId?: number;
  /** Cơ sở đang chọn trong khu vận hành; admin chưa chọn = null → không kêu */
  storeId: number | null;
}

/**
 * Chuông + nháy tiêu đề tab cho khu vận hành (spec realtime §5.4).
 * - Bếp (staff/manager/admin): kêu khi có đơn mới tạo ở cơ sở đang chọn.
 * - Shipper: KHÔNG kêu ở đây — ShipperOrdersPage tự kêu khi thấy đơn mới; ở đây chỉ nháy tab.
 */
export const useOrderAlerts = ({ role, userId, storeId }: OrderAlertsOptions) => {
  const baseTitleRef = useRef<string | null>(null);
  const unseenRef = useRef(0);

  useEffect(() => {
    const restoreTitle = () => {
      if (document.visibilityState !== 'visible' || baseTitleRef.current === null) return;
      document.title = baseTitleRef.current;
      baseTitleRef.current = null;
      unseenRef.current = 0;
    };
    document.addEventListener('visibilitychange', restoreTitle);
    return () => document.removeEventListener('visibilitychange', restoreTitle);
  }, []);

  useRealtime((message) => {
    if (message.type !== 'order') return;
    const signal = message.payload;
    const isKitchen = role === 'STAFF' || role === 'MANAGER' || role === 'ADMIN';
    const newOrderHere = isKitchen && storeId != null && signal.storeId === storeId && signal.kind === 'CREATED';
    const assignedToMe =
      role === 'SHIPPER' && signal.kind === 'SHIPPER_ASSIGNED' && userId != null && signal.shipperId === userId;
    if (!newOrderHere && !assignedToMe) return;

    if (newOrderHere) playAlertSound();
    if (document.visibilityState === 'hidden') {
      if (baseTitleRef.current === null) baseTitleRef.current = document.title;
      unseenRef.current += 1;
      document.title = `(${unseenRef.current}) ${newOrderHere ? 'Đơn mới' : 'Đơn được giao'} — ${baseTitleRef.current}`;
    }
  });
};
```

Kiểm tra tên kiểu: `RoleName` được export từ `frontend/src/types/auth.ts` (dòng `role: RoleName;`). Nếu kiểu nằm ở file khác, sửa đường import cho khớp (`grep -rn "export type RoleName" frontend/src/types`).

- [ ] **Step 4: `components/layout/AlertSoundToggle.tsx`**

```tsx
import { useState } from 'react';
import { Bell, BellOff } from 'lucide-react';
import { isAlertSoundEnabled, playAlertSound, setAlertSoundEnabled, unlockAlertSound } from '../../utils/alertSound';

/**
 * Nút bật/tắt âm báo. Trình duyệt chặn âm thanh tới khi người dùng bấm vào trang, nên bật âm
 * phải đi qua cú bấm này (mở khoá AudioContext) và kêu thử một tiếng.
 */
export const AlertSoundToggle = () => {
  const [enabled, setEnabled] = useState(isAlertSoundEnabled);

  const toggle = async () => {
    const next = !enabled;
    setAlertSoundEnabled(next);
    setEnabled(next);
    if (next && (await unlockAlertSound())) playAlertSound();
  };

  return (
    <button
      type="button"
      className={`dash__sound${enabled ? ' dash__sound--on' : ''}`}
      onClick={() => void toggle()}
      aria-pressed={enabled}
      title={enabled ? 'Tắt âm báo đơn mới' : 'Bật âm báo đơn mới'}
    >
      {enabled ? <Bell size={15} /> : <BellOff size={15} />}
      {enabled ? 'Âm báo: bật' : 'Âm báo: tắt'}
    </button>
  );
};
```

- [ ] **Step 5: Gắn vào `DashboardLayout`**

Thêm import:
```tsx
import { useOrderAlerts } from '../../hooks/useOrderAlerts';
import { AlertSoundToggle } from './AlertSoundToggle';
```
Trong component, ngay sau dòng `const inboxCounts = useInboxCounts(...)`:
```tsx
  useOrderAlerts({ role: user?.role, userId: user?.id, storeId: scope.storeId });
```
Trong JSX, ngay sau khối `<div className="dash__brand">…</div>`:
```tsx
        <div className="dash__sound-row">
          <AlertSoundToggle />
        </div>
```

Thêm vào cuối `frontend/src/styles/layout.css`:
```css
/* Nút bật/tắt âm báo đơn mới — ngay dưới logo, hiện cả trên điện thoại */
.dash__sound-row {
  padding: var(--space-3) var(--space-4) 0;
}

.dash__sound {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-height: 36px;
  padding: 0 var(--space-3);
  border: 2px solid rgba(255, 253, 247, .25);
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--stone-300);
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
}

.dash__sound:hover {
  border-color: var(--primary-300);
  color: var(--cream);
}

.dash__sound--on {
  border-color: var(--primary-300);
  color: var(--primary-200);
}

@media (max-width: 900px) {
  .dash__sound-row {
    padding: var(--space-2) var(--space-4) 0;
  }
}
```

- [ ] **Step 6: Chỉ báo kết nối thật trong `PageHeader`**

Thêm import:
```tsx
import { useRealtimeStatus } from '../../context/useRealtime';
import type { RealtimeStatus } from '../../realtime/types';
```
Thêm trên component:
```tsx
const LIVE_LABEL: Record<RealtimeStatus, string> = {
  live: 'Trực tiếp',
  reconnecting: 'Đang nối lại…',
  offline: 'Ngoại tuyến · tự làm mới 30 giây',
};

/** Trạng thái thật của luồng realtime (spec realtime §5.6) — thay cho nhãn "Realtime Sync" trang trí */
const LiveIndicator = () => {
  const status = useRealtimeStatus();
  return (
    <span className={`page-head__pulse page-head__pulse--${status}`} role="status" title="Kết nối cập nhật tức thì">
      <span className="page-head__pulse-dot" aria-hidden="true" />
      {LIVE_LABEL[status]}
    </span>
  );
};
```
Thay khối cũ:
```tsx
      {onRefresh && (
        <span className="page-head__pulse" title="Đồng bộ thời gian thực với máy chủ">
          <span className="page-head__pulse-dot" aria-hidden="true" />
          Realtime Sync
        </span>
      )}
```
bằng:
```tsx
      {onRefresh && <LiveIndicator />}
```

Thêm vào cuối `frontend/src/styles/components/page-header.css` (selector 3 lớp để thắng `.dash .page-head__pulse-dot` trong backhouse.css):
```css
/* Trạng thái luồng realtime: vàng = đang nối lại, xám = ngoại tuyến (polling dự phòng) */
.page-head__pulse.page-head__pulse--reconnecting .page-head__pulse-dot {
  background: var(--warning);
  animation: none;
}

.page-head__pulse.page-head__pulse--offline .page-head__pulse-dot {
  background: var(--stone-400);
  animation: none;
}
```

- [ ] **Step 7: Typecheck + lint + build**

Run (trong `frontend/`): `npx tsc -p tsconfig.app.json --noEmit && npx oxlint src && npm run build`
Expected: không lỗi.

- [ ] **Step 8: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 10: Toast trạng thái đơn cho khách

**Files:**
- Create: `frontend/src/hooks/useOrderStatusToasts.ts`
- Modify: `frontend/src/components/layout/CustomerLayout.tsx`

**Interfaces:**
- Consumes: `useRealtime` (Task 7); `useToast` (`components/ui`); `useAuth`; `useLocation`.
- Produces: `useOrderStatusToasts()`.

- [ ] **Step 1: Viết hook**

```ts
import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { useToast } from '../components/ui';
import { useAuth } from '../context/useAuth';
import { useRealtime } from '../context/useRealtime';
import type { OrderStatus } from '../types/order';

const MESSAGE: Partial<Record<OrderStatus, (code: string) => string>> = {
  CONFIRMED: (code) => `Lò đã nhận đơn ${code} của bạn!`,
  PREPARING: () => 'Bếp đang làm bánh của bạn 🔥',
  READY_FOR_PICKUP: () => 'Bánh xong rồi, đang chờ shipper tới lấy',
  DELIVERING: () => 'Shipper đang trên đường tới 🛵',
  DELIVERED: () => 'Giao xong, chúc bạn ngon miệng!',
  CANCELLED: (code) => `Đơn ${code} đã bị huỷ`,
  FAILED: (code) => `Đơn ${code} giao không thành công`,
};

/**
 * Báo khách khi đơn đổi trạng thái, ở bất kỳ trang nào của khu khách (spec realtime §5.5).
 * Chỉ cho vai trò CUSTOMER (admin/staff xem trang khách nhận tin của mọi đơn). Bỏ qua khi đang
 * mở trang theo dõi của chính đơn đó — trang này tự báo và là nơi duy nhất khách tự huỷ đơn.
 */
export const useOrderStatusToasts = () => {
  const toast = useToast();
  const { user } = useAuth();
  const { pathname } = useLocation();
  const pathRef = useRef(pathname);
  useEffect(() => {
    pathRef.current = pathname;
  }, [pathname]);

  const isCustomer = user?.role === 'CUSTOMER';

  useRealtime((message) => {
    if (!isCustomer || message.type !== 'order' || message.payload.kind !== 'STATUS_CHANGED') return;
    const { orderCode, status } = message.payload;
    if (pathRef.current === `/orders/${orderCode}`) return;
    const text = MESSAGE[status]?.(orderCode);
    if (!text) return;
    if (status === 'CANCELLED' || status === 'FAILED') toast.error(text);
    else toast.success(text);
  });
};
```

- [ ] **Step 2: Gắn vào `CustomerLayout`**

Thêm import `import { useOrderStatusToasts } from '../../hooks/useOrderStatusToasts';` và gọi ngay sau dòng `const { pathname } = useLocation();`:
```tsx
  useOrderStatusToasts();
```

- [ ] **Step 3: Typecheck + lint + build + test**

Run (trong `frontend/`): `npx tsc -p tsconfig.app.json --noEmit && npx oxlint src && npm run build && npm run test`
Expected: không lỗi; 12 test PASS.

- [ ] **Step 4: Liệt kê thay đổi (không commit)**

Run: `git status --short`

---

### Task 11: Kiểm tra toàn bộ + kịch bản tay

**Files:** không tạo file mới (chỉ chạy kiểm tra).

- [ ] **Step 1: Toàn bộ test backend**

Run: `./mvnw -q test`
Expected: PASS.

- [ ] **Step 2: Toàn bộ kiểm tra frontend**

Run (trong `frontend/`): `npm run test && npx oxlint src && npm run build`
Expected: PASS, không lỗi.

- [ ] **Step 3: Kịch bản tay (spec §7)** — chạy backend + `npm run dev`, làm lần lượt:
1. Cửa sổ A đăng nhập khách, mở `/orders/{mã}` của một đơn mới; cửa sổ B (ẩn danh) đăng nhập staff/admin, mở `/staff/orders` đúng cơ sở. Khách đặt đơn mới → B kêu "ting", đơn hiện ngay.
2. B chuyển đơn sang "Đang làm" → A nhảy trạng thái ngay (toast có sẵn của trang theo dõi).
3. Khách ở trang chủ (không phải trang theo dõi) → staff đổi trạng thái → khách thấy toast "Bếp đang làm bánh của bạn 🔥".
4. B phân công shipper → cửa sổ shipper kêu và hiện đơn.
5. Chuyển B sang tab khác, đặt đơn mới → tiêu đề tab B thành "(1) Đơn mới — …"; quay lại tab → tiêu đề trở lại.
6. Tắt backend → ô trạng thái "Đang nối lại…"; bật lại → "● Trực tiếp" và dữ liệu khớp ngay.
7. Gửi phản hồi từ trang Liên hệ → huy hiệu "Phản hồi" của admin tăng ngay.
8. Đăng xuất → Network không còn request `realtime/stream` đang treo.
9. Mở 6 tab cùng tài khoản → tab đầu chuyển "Đang nối lại…" (bị đóng do giới hạn 5), các tab khác vẫn "Trực tiếp".

- [ ] **Step 4: Liệt kê thay đổi cuối cùng (không commit)**

Run: `git status --short` — gửi danh sách cho người dùng tự commit.
