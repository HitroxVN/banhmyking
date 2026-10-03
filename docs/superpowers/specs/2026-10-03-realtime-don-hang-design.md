# Thiết kế: Thông báo realtime cho đơn hàng và hộp thư (dự án con E)

- **Ngày:** 2026-10-03
- **Trạng thái:** Đã duyệt
- **Nhánh:** `feature/realtime-don-hang` (tạo từ `feature/ui-admin-bep-sau`)
- **Phụ thuộc:** phân quyền theo cơ sở của dự án con A (`docs/superpowers/specs/2026-10-01-multi-store-design.md`); huy hiệu hộp thư của dự án con D (`docs/superpowers/specs/2026-10-02-news-careers-feedback-design.md`)
- **Tiếp theo:** dự án con F (tích điểm, hạng thành viên) sẽ nghe `OrderChangedEvent` của E để cộng điểm khi đơn giao thành công

## 1. Bối cảnh và mục tiêu

Hiện mọi màn "sống" đều tự hỏi server định kỳ:

| Màn | Cách làm hiện tại |
|---|---|
| Hàng đơn bếp (`StaffOrderQueuePage`) | `setInterval` 3 giây |
| Shipper (`ShipperOrdersPage`) | `setInterval` 3 giây |
| Theo dõi đơn (`OrderTrackingPage`) | `usePolling` 5 giây, dừng khi đơn kết thúc |
| Đơn của tôi (`OrdersPage`) | `usePolling` 15 giây |
| Huy hiệu hộp thư (`useInboxCounts`) | `usePolling` 60 giây |
| Quản lý đơn (`AdminOrdersPage`) | chỉ khi bấm "Làm mới" |

Cách này vừa tốn request (bếp mở cả ngày = 1.200 request/giờ/tab) vừa trễ tới vài giây; ô "Realtime Sync" trên đầu trang chỉ là trang trí.

E thay polling bằng **luồng sự kiện đẩy từ server** để: bếp nghe "ting" ngay khi có đơn, shipper thấy ngay đơn được giao, khách thấy trạng thái đơn nhảy tức thì, admin thấy đơn và hộp thư cập nhật không cần bấm.

### Quyết định đã chốt

| # | Quyết định |
|---|---|
| E1 | Dùng **Server-Sent Events** (`SseEmitter` của Spring MVC), không dùng WebSocket — mọi sự kiện chỉ đi một chiều server → trình duyệt; thao tác vẫn qua REST |
| E2 | Sự kiện là **tín hiệu, không mang dữ liệu nghiệp vụ**: trang nhận tín hiệu rồi gọi lại API REST sẵn có. Không nhân đôi DTO hay logic phân quyền dữ liệu |
| E3 | Phục vụ cả 4 nhóm: **bếp/manager, shipper, khách, admin** (đơn hàng + hộp thư) |
| E4 | Tab ở nền: **âm báo "ting" + nháy tiêu đề tab**. Không dùng Notification API của hệ điều hành |
| E5 | Mất kết nối thì **tự quay về polling 30 giây** cho tới khi nối lại được; nối lại thì **tải bù một lần** |
| E6 | Thêm **Vitest** (dev dependency) để test các hàm thuần phía frontend |

### Ngoài phạm vi E
Chạy nhiều instance backend (cần Redis pub/sub); thông báo khi đã đóng tab (PWA push); thông báo hệ điều hành; chat hai chiều; gửi email/SMS khi đổi trạng thái; lưu lịch sử thông báo cho người dùng xem lại.

## 2. Sự kiện miền (backend)

Không có thay đổi database (không có migration).

### 2.1 `OrderChangedEvent` (record, gói `event`)

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `orderId` | `Long` | |
| `orderCode` | `String` | |
| `kind` | `OrderChangeKind` | `CREATED` \| `STATUS_CHANGED` \| `SHIPPER_ASSIGNED` \| `STORE_TRANSFERRED` \| `UPDATED` |
| `fromStatus` | `OrderStatus` | |
| `toStatus` | `OrderStatus` | |
| `customerId` | `Long` | |
| `storeId` | `Long` | cơ sở hiện tại (sau thay đổi) |
| `shipperId` | `Long` | `null` nếu chưa gán |
| `previousStoreId` | `Long` | chỉ khi `STORE_TRANSFERRED`, ngược lại `null` |
| `previousShipperId` | `Long` | chỉ khi shipper bị đổi/gỡ, ngược lại `null` |

`kind`:
- `CREATED`, `SHIPPER_ASSIGNED`, `STORE_TRANSFERRED`: hàm gọi truyền **tường minh** — `createFromCart` ghi lịch sử `PENDING → PENDING` nên không suy ra được từ trạng thái; `assignShipper`, `rejectAssignedOrder`, `transferStore` cũng ghi lịch sử cùng trạng thái
- còn lại suy ra: `fromStatus != toStatus` → `STATUS_CHANGED`, ngược lại → `UPDATED` (ví dụ hoàn tiền, ghi chú thanh toán `PENDING → PENDING`)

### 2.2 Chỗ phát sự kiện

Hầu hết đường đổi trạng thái đơn đã gọi một trong hai hàm ghi lịch sử, nên **chỉ phát sự kiện ở hai chỗ này** thay vì rải ra 6 nơi. Riêng `rejectAssignedOrder` đang tự tạo `OrderStatusHistory` — đổi sang gọi `recordHistory` (sau khi gỡ shipper) để cũng phát sự kiện:
- `OrderServiceImpl.recordHistory(Order, fromStatus, toStatus, …)`: dùng bởi `createFromCart`, `confirmDelivery`, `cancelOrder`, `cancelStalePendingOrders` (job `OrderMaintenanceJob`), `updateOrderStatus`, `assignShipper`, `rejectAssignedOrder`, `transferStore`
- `PaymentServiceImpl.recordPaymentHistory(Order, fromStatus, toStatus, note)`: dùng bởi `confirmPaidOrder` (đối soát của staff và webhook SePay)

Hai hàm nhận thêm tham số tuỳ chọn để biết `kind`, `previousStoreId`, `previousShipperId`. Các hàm gọi lấy giá trị cũ **trước khi** gán giá trị mới. Sự kiện phát qua `ApplicationEventPublisher` bên trong giao dịch hiện có.

### 2.3 `InboxChangedEvent` (record)

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `type` | `InboxType` | `FEEDBACK` \| `JOB_APPLICATION` |
| `storeId` | `Long` | cơ sở liên quan; `null` = toàn chuỗi |

Phát khi:
- `FeedbackService.submit` lưu thành công (trả `true`)
- `JobApplicationService.submit` lưu thành công
- đổi trạng thái xử lý của phản hồi hoặc hồ sơ ứng tuyển (vì số "mới" trên huy hiệu của người khác thay đổi)

## 3. Phân phối sự kiện (`RealtimeHub`)

### 3.1 Kết nối

`RealtimeHub` (Spring `@Component`) giữ các kết nối đang mở trong bộ nhớ:
`ConcurrentHashMap<String connectionId, RealtimeConnection>`, với `RealtimeConnection(userId, role, storeId, SseEmitter emitter, Instant openedAt)`.

- `register(user)` tạo `SseEmitter` với timeout **30 phút**, gửi ngay tin `ready`, đăng ký `onCompletion` / `onTimeout` / `onError` để tự gỡ.
- Mỗi người dùng tối đa **5 kết nối**: kết nối thứ 6 làm **kết nối cũ nhất** bị đóng (`complete()`), không từ chối kết nối mới.
- `@Scheduled(fixedRate = 25s)` gửi dòng chú thích `: ping` cho mọi kết nối để giữ kết nối qua proxy và phát hiện kết nối chết.
- Gửi thất bại (`IOException`, `IllegalStateException`) thì gỡ kết nối và ghi log mức `debug`. **Không bao giờ ném lỗi ra ngoài** làm hỏng nghiệp vụ.
- `storeId` của staff/manager/shipper lấy từ người dùng **lúc kết nối**. Đổi cơ sở cho nhân viên có hiệu lực ở lần kết nối kế tiếp (tối đa 30 phút, hoặc khi đăng nhập lại).

### 3.2 Ai nhận tin gì

`OrderChangedEvent` gửi tới kết nối thoả **một trong** các điều kiện:

| Vai trò | Điều kiện nhận |
|---|---|
| `CUSTOMER` | `userId == customerId` |
| `STAFF`, `MANAGER` | `storeId == event.storeId` hoặc `storeId == event.previousStoreId` |
| `SHIPPER` | `userId == event.shipperId` hoặc `userId == event.previousShipperId` |
| `ADMIN` | luôn nhận |

`InboxChangedEvent` gửi tới:

| Vai trò | Điều kiện nhận |
|---|---|
| `ADMIN` | luôn nhận |
| `MANAGER` | `event.storeId == null` hoặc `storeId == event.storeId` |
| khác | không nhận |

Các quy tắc này khớp phạm vi dữ liệu mà từng vai trò vốn được xem qua API. Tin chỉ là tín hiệu (E2) nên dù quy tắc rộng hơn cần thiết cũng không lộ dữ liệu.

### 3.3 Thời điểm gửi

`RealtimeEventListener` dùng `@TransactionalEventListener(phase = AFTER_COMMIT)`:
- chỉ gửi **sau khi giao dịch đã lưu** — tránh trình duyệt gọi lại API trước khi dữ liệu kịp ghi;
- giao dịch rollback thì không gửi gì;
- `fallbackExecution = true` để sự kiện phát ngoài giao dịch (nếu có) vẫn được gửi.

### 3.4 Định dạng tin trên luồng

```
event: ready
data: {"connectionId":"…"}

event: order
data: {"orderCode":"BMK-20261003-TRCAC","status":"PREPARING","kind":"STATUS_CHANGED","storeId":1,"shipperId":null,"previousStoreId":null}

event: inbox
data: {"type":"FEEDBACK"}

: ping
```

## 4. API và bảo mật

### 4.1 Endpoint
`GET /api/v1/realtime/stream` → `text/event-stream`, trả `SseEmitter` từ `RealtimeHub.register`. Yêu cầu đăng nhập (mọi vai trò).

### 4.2 Xác thực
JWT qua header `Authorization: Bearer …` như mọi API khác, đi qua `JwtAuthenticationFilter` hiện có. Frontend mở luồng bằng `fetch` nên gửi được header. **Không** đưa token lên query string.

### 4.3 `SecurityConfig`
- `/api/v1/realtime/**` đã nằm trong quy tắc `"/api/v1/**").authenticated()` sẵn có — không cần thêm quy tắc riêng.
- Thêm `dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`. SSE trả dữ liệu dần qua async dispatch; Spring Security 6+ kiểm tra quyền lại ở bước này (bộ lọc JWT không chạy lại cho async dispatch), thiếu dòng này thì khi luồng kết thúc sẽ bị lỗi `AccessDenied` sau khi phản hồi đã bắt đầu. **Không** mở `ERROR` dispatch để không đổi cách trả lỗi 401/403 hiện có.

### 4.4 Vận chuyển
- Header phản hồi: `Cache-Control: no-cache`, `X-Accel-Buffering: no` (phòng khi sau này đặt sau Nginx).
- Hiện server không bật nén gzip (`server.compression` không cấu hình), Vite dev proxy (`/api` → 8080) chuyển luồng không gom — đã kiểm tra `vite.config.ts`. Nếu sau này bật nén thì phải loại trừ `text/event-stream`.

## 5. Frontend

### 5.1 Client luồng (`src/realtime/`)
- `sseParser.ts`: hàm thuần nhận các khúc văn bản, trả danh sách `{event, data}`. Xử lý tin bị cắt giữa hai khúc, nhiều tin trong một khúc, dòng chú thích `:`, `\r\n`.
- `backoff.ts`: hàm thuần cho lịch chờ nối lại `1s → 2s → 5s → 10s → 30s` (giữ 30 giây từ lần thứ 5), cộng ngẫu nhiên ±20% để các tab không nối lại cùng lúc. Kết nối giữ được ≥ 60 giây thì đặt lại về bước đầu.
- `realtimeClient.ts`: mở `fetch('/api/v1/realtime/stream', { headers: Authorization, signal })`, đọc `response.body` bằng `TextDecoder` + `sseParser`, tự nối lại theo `backoff`.
  - Phản hồi `401`: gọi `authApi.getMe()` qua axios để kích hoạt luồng làm mới token sẵn có trong interceptor (`axiosClient.ts`, có chống gọi refresh trùng), rồi nối lại với token mới. Làm mới thất bại thì dừng, để luồng đăng xuất hiện có xử lý. Không tự viết lại logic refresh.
  - Đăng xuất / unmount: `AbortController.abort()`.

### 5.2 `RealtimeProvider` + hook
- `RealtimeProvider` đặt ngay dưới `AuthProvider` trong `App.tsx`. Chỉ mở luồng khi `isAuthenticated`; đóng khi đăng xuất. **Một kết nối cho cả app.**
- Cung cấp `status: 'live' | 'reconnecting' | 'offline'` và `subscribe(type, handler)`.
- Khi nhận `ready` sau một lần mất kết nối, phát tín hiệu nội bộ `resync` để mọi trang đang mở tải lại đúng một lần (E5).
- `useRealtime(type, handler)`: đăng ký nghe một loại tin.
- `useLiveRefresh(refetch, { match, fallbackMs = 30000 })`:
  - gọi `refetch` khi có tin khớp `match` hoặc khi có `resync`;
  - nhiều tin trong **300ms** gộp thành một lần gọi;
  - chỉ chạy polling dự phòng `fallbackMs` khi `status !== 'live'`.

### 5.3 Thay polling ở các màn

| Màn | Thay bằng | `match` |
|---|---|---|
| `StaffOrderQueuePage` | `useLiveRefresh` | `order.storeId == cơ sở đang xem` hoặc `order.previousStoreId == cơ sở đang xem` (đơn vừa chuyển đi) |
| `ShipperOrdersPage` | `useLiveRefresh` | mọi tin `order` (hub đã lọc theo shipper); giữ logic so danh sách + chuông có sẵn |
| `OrderTrackingPage` | `useLiveRefresh` | `order.orderCode == mã đơn đang xem`; vẫn dừng khi đơn kết thúc |
| `OrdersPage` | `useLiveRefresh` | mọi tin `order` |
| `AdminOrdersPage` | `useLiveRefresh` | mọi tin `order` |
| `useInboxCounts` | `useLiveRefresh` | mọi tin `inbox`; giữ lắng nghe `INBOX_CHANGED_EVENT` nội bộ như cũ |

`useLiveRefresh` giữ các hành vi tốt của `usePolling`: tải ngay khi quay lại tab / focus cửa sổ, nghe `orderSyncChannel` (BroadcastChannel giữa các tab cùng trình duyệt). Hai `setInterval` 3 giây trong trang bếp và shipper được bỏ; `usePolling` không còn ai dùng thì xoá.

### 5.4 Âm báo và nháy tab (`useOrderAlerts`)
Chỉ chạy trong khu vận hành (`DashboardLayout`).
- **Tiếng "ting"** dùng lại `playNotificationSound` sẵn có (`utils/orderSyncChannel.ts`, Web Audio), sửa lại để dùng **một** `AudioContext` dùng chung (hiện mỗi lần kêu tạo một cái mới) và tôn trọng nút bật/tắt bên dưới.
- **Ai kêu khi nào:**
  - staff/manager/admin: tin `order` có `kind = CREATED`, thuộc cơ sở đang chọn (`useStoreScope`); admin chưa chọn cơ sở thì không kêu. Không kêu khi đơn chuyển sang `CONFIRMED` — tránh kêu cho chính thao tác nhân viên vừa bấm và kêu hai lần cho đơn chuyển khoản;
  - shipper: **không thêm chuông mới** — `ShipperOrdersPage` đã tự so danh sách và kêu + toast khi có đơn mới được giao; realtime làm việc tải lại xảy ra ngay nên chuông có sẵn kêu tức thì. `useOrderAlerts` với shipper chỉ lo nháy tab, cho tin `kind = SHIPPER_ASSIGNED` có `shipperId` là mình.
- **Nháy tiêu đề tab** khi `document.hidden`: đếm số tin đáng báo, đặt tiêu đề `(n) Đơn mới — <tên web>`; khi `visibilitychange` sang hiện thì trả tiêu đề cũ và đặt lại bộ đếm.
- **Nút "🔔 Bật âm báo"** trong thanh đầu khu vận hành: trình duyệt chặn âm thanh tới khi người dùng tương tác, nên `AudioContext` chỉ được tạo/resume sau cú bấm. Lựa chọn bật/tắt lưu `localStorage` (bọc try/catch). Âm thanh bị chặn thì vẫn còn nháy tab và dữ liệu tự cập nhật.

### 5.5 Báo cho khách
Trong khu khách (`CustomerLayout`), **chỉ với tài khoản vai trò `CUSTOMER`** (admin/staff xem trang khách nhận tin của mọi đơn nên không hiện), tin `order` có `kind = STATUS_CHANGED` hiện toast thân thiện theo `toStatus`:

| `toStatus` | Nội dung |
|---|---|
| `CONFIRMED` | "Lò đã nhận đơn {mã} của bạn!" |
| `PREPARING` | "Bếp đang làm bánh của bạn 🔥" |
| `READY_FOR_PICKUP` | "Bánh xong rồi, đang chờ shipper tới lấy" |
| `DELIVERING` | "Shipper đang trên đường tới 🛵" |
| `DELIVERED` | "Giao xong, chúc bạn ngon miệng!" |
| `CANCELLED` / `FAILED` | "Đơn {mã} đã bị huỷ / giao không thành công" (toast lỗi) |

Toast dạng chữ (API toast hiện chỉ nhận chuỗi). **Bỏ qua toast khi khách đang mở trang theo dõi của chính đơn đó** (`/orders/{mã}`): trang này đã tự báo khi trạng thái đổi, và cũng là nơi duy nhất khách tự huỷ đơn — nên không cần cơ chế riêng để nhận biết thao tác của chính khách.

### 5.6 Chỉ báo kết nối thật
`PageHeader` hiện có ô "Realtime Sync" khi có `onRefresh`. Đổi thành đọc `status` từ `RealtimeProvider`:
- `live`: "● Trực tiếp" (xanh rau)
- `reconnecting`: "Đang nối lại…" (vàng)
- `offline`: "Ngoại tuyến · tự làm mới 30 giây" (xám)

Nút "Làm mới" giữ nguyên.

## 6. Xử lý lỗi

| Tình huống | Cách xử lý |
|---|---|
| Mất mạng / backend khởi động lại | Nối lại theo `backoff`; trong lúc đó polling 30 giây; chỉ báo "Đang nối lại…" |
| Lỡ sự kiện trong lúc mất kết nối | `ready` sau khi nối lại → `resync` → mọi trang tải lại một lần |
| Tin dồn / trùng | Gộp 300ms; tải lại là GET, lặp vô hại |
| Tải lại sau tin bị lỗi | Mỗi trang giữ cách báo lỗi sẵn có |
| Token hết hạn | Làm mới qua interceptor rồi nối lại; thất bại → luồng đăng xuất hiện có |
| > 5 kết nối của một người | Đóng kết nối cũ nhất; tab đó chuyển polling rồi tự nối lại (và có thể đẩy tab khác ra) — chấp nhận được với giới hạn 5 |
| Đăng xuất | `abort()` ngay; backend gỡ khi `onError`/`onCompletion` |
| Gửi tin lỗi phía server | Gỡ kết nối, log `debug`; nghiệp vụ không bị ảnh hưởng |
| Âm thanh bị chặn | Nút "Bật âm báo" vẫn hiện; nháy tab + tự cập nhật vẫn chạy |

## 7. Kiểm thử

### Backend (`spring-boot-starter-test` sẵn có)
- **`RealtimeHubTest`** (unit, không cần Spring context):
  - khách chỉ nhận đơn của mình;
  - staff/manager chỉ nhận đơn của cơ sở mình, và nhận khi đơn bị chuyển **đi khỏi** cơ sở mình (`previousStoreId`);
  - shipper nhận khi được gán, và nhận khi bị gỡ (`previousShipperId`);
  - admin nhận mọi tin `order` và `inbox`;
  - manager chỉ nhận `inbox` của cơ sở mình hoặc toàn chuỗi; staff/shipper/khách không nhận `inbox`;
  - kết nối thứ 6 của cùng người dùng làm kết nối cũ nhất bị đóng;
  - (hub nhận một `RealtimeSink` thay vì `SseEmitter` trực tiếp, nên test dùng sink giả, không cần Spring context)
  - emitter ném lỗi khi gửi thì bị gỡ, các kết nối khác vẫn nhận.
- **Phát sự kiện**: mỗi đường `createFromCart`, `confirmPaidOrder`, `updateOrderStatus`, `confirmDelivery`, `cancelOrder`, `cancelStalePendingOrders`, `assignShipper`, `rejectAssignedOrder`, `transferStore` phát **đúng một** `OrderChangedEvent` với `kind`, `fromStatus`, `toStatus`, `previous*` đúng (mock `ApplicationEventPublisher`, theo cách test service hiện có).
- **`RealtimeStreamSecurityTest`** (filter chain + JWT thật, theo kiểu `NewsCareersSecurityTest`): chưa đăng nhập → 401; đã đăng nhập → `200`, `Content-Type: text/event-stream`, header `X-Accel-Buffering: no`, nội dung có tin `ready`.

### Frontend (thêm Vitest, `npm run test`)
- `sseParser`: tin đầy đủ; tin bị cắt giữa hai khúc; nhiều tin một khúc; dòng `: ping`; `\r\n`; trường `data` nhiều dòng.
- `backoff`: đúng lịch, có giới hạn trên, đặt lại sau kết nối ổn định, jitter trong ±20%.
- logic gộp 300ms của `useLiveRefresh` (tách thành hàm thuần `createCoalescer`, test bằng fake timers).

### Kiểm tra tay (ghi vào hướng dẫn chạy)
1. Mở hai cửa sổ: khách (trang theo dõi đơn) và bếp (hàng đơn, cơ sở tương ứng). Khách đặt đơn → bếp kêu "ting", đơn hiện ngay không cần chờ.
2. Bếp chuyển "Đang làm" → trang khách nhảy trạng thái + toast "Bếp đang làm bánh của bạn".
3. Bếp phân công shipper → cửa sổ shipper kêu và hiện đơn.
4. Chuyển bếp sang tab khác, đặt đơn mới → tiêu đề tab thành "(1) Đơn mới — …".
5. Tắt backend → chỉ báo "Đang nối lại…", trang vẫn tự làm mới 30 giây; bật lại → "● Trực tiếp" và dữ liệu khớp ngay.
6. Gửi phản hồi từ trang Liên hệ → huy hiệu "Phản hồi" của admin tăng ngay.
7. Đăng xuất → tab Network không còn request `realtime/stream` treo.

## 8. Rủi ro

| Rủi ro | Giảm thiểu |
|---|---|
| Chỉ đúng với **một instance** backend (kết nối trong bộ nhớ) | Ghi rõ; khi cần mở rộng thì thêm Redis pub/sub ở `RealtimeEventListener`, không phải sửa frontend |
| Mỗi kết nối giữ một request mở → tốn luồng xử lý | `SseEmitter` dùng async nên không giữ thread Tomcat; giới hạn 5 kết nối/người |
| Proxy/nén gom dữ liệu làm tin đến trễ | Header `X-Accel-Buffering: no`, `Cache-Control: no-cache`; không bật nén cho `text/event-stream` |
| Đổi cơ sở của nhân viên không có hiệu lực ngay với luồng đang mở | Có hiệu lực ở lần nối lại (≤ 30 phút) hoặc khi đăng nhập lại; tin là tín hiệu nên không lộ dữ liệu |
| Gửi tin đồng bộ trên luồng request (sau commit) và luồng scheduler (ping) — một socket bị nghẽn làm chậm luồng đó | chấp nhận ở quy mô một instance; nếu cần thì chuyển sang executor riêng |
| Âm báo gây phiền khi nhiều tab cùng mở | Chỉ kêu trong màn hàng đơn bếp / shipper; có nút tắt |
