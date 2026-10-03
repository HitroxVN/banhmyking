# Thiết kế: Tin tức, Tuyển dụng, Phản hồi (dự án con D)

- **Ngày:** 2026-10-02
- **Trạng thái:** Đã triển khai
- **Nhánh:** `feature/news-careers` (tạo từ `feature/combo-sale`)
- **Phụ thuộc:** dự án con A (cơ sở, MANAGER, `StoreAccessGuard`) — `docs/superpowers/specs/2026-10-01-multi-store-design.md`

## 1. Bối cảnh và mục tiêu

Theo hướng bamiking.vn, trang web cần thêm nội dung ngoài bán hàng: tin tức/khuyến mãi, tuyển dụng theo cơ sở, và kênh phản hồi có xử lý. Hiện trang Liên hệ chỉ có hotline + `mailto`; dự án đã có `EmailService`, `FileStorageService` (ảnh công khai trong `uploads/`), cơ chế xoá mềm và phân quyền theo cơ sở từ A.

D gồm **3 mô-đun độc lập** (bảng, API, trang riêng), không đụng luồng đặt hàng/tính tiền.

### Quyết định đã chốt

| # | Quyết định |
|---|---|
| D1 | Bài tin tức và mô tả tin tuyển dụng soạn bằng **trình soạn Markdown đơn giản** (thanh công cụ + xem trước), hiển thị an toàn |
| D2 | Ứng tuyển bằng **form công khai + CV đính kèm** (PDF/JPG/PNG ≤ 5MB, không bắt buộc) |
| D3 | Phản hồi **gắn cơ sở + đơn hàng (tuỳ chọn)**, có trạng thái xử lý và email báo admin |
| D4 | **Chỉ ADMIN** đăng tin tức và tin tuyển dụng; **MANAGER** xử lý hồ sơ ứng tuyển và phản hồi **của cơ sở mình** |

### Ngoài phạm vi D
Trả lời khách qua email trong trang quản trị; bình luận bài viết; danh mục/thẻ tin tức; MANAGER tự đăng tin tuyển; Google reCAPTCHA; đa ngôn ngữ.

## 2. Mô hình dữ liệu (migration V15)

Mọi bảng có `id BIGINT AUTO_INCREMENT`, `created_at`, `updated_at` (theo `BaseEntity`). Bảng có xoá mềm dùng `is_deleted BOOLEAN NOT NULL DEFAULT FALSE` như các bảng hiện có.

### 2.1 `news_posts`
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `title` | `VARCHAR(200) NOT NULL` | |
| `slug` | `VARCHAR(220) NOT NULL UNIQUE` | tự sinh từ tiêu đề (bỏ dấu, gạch nối), sửa được; trùng → thêm `-2`, `-3`… |
| `cover_image_url` | `VARCHAR(500) NULL` | dùng chức năng tải ảnh sẵn có |
| `summary` | `VARCHAR(500) NULL` | tóm tắt hiện trên thẻ |
| `content` | `MEDIUMTEXT NOT NULL` | Markdown |
| `status` | `VARCHAR(20) NOT NULL` | `DRAFT` \| `PUBLISHED` |
| `published_at` | `DATETIME NULL` | bắt buộc khi `PUBLISHED`; ở tương lai = **hẹn giờ** |
| `pinned` | `BOOLEAN NOT NULL DEFAULT FALSE` | |
| `author_id` | `BIGINT NULL` FK → `users.id` | |
| `is_deleted` | | |

Khách thấy bài khi: `status = PUBLISHED` ∧ `published_at ≤ now` (Clock, giờ Việt Nam) ∧ chưa xoá.

### 2.2 `job_postings`
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `title` | `VARCHAR(200) NOT NULL` | vị trí |
| `slug` | `VARCHAR(220) NOT NULL UNIQUE` | như tin tức |
| `employment_type` | `VARCHAR(20) NOT NULL` | `FULL_TIME` \| `PART_TIME` \| `SEASONAL` |
| `salary_text` | `VARCHAR(100) NULL` | vd "22–25k/giờ", "Thoả thuận" |
| `headcount` | `INT NULL` | số lượng cần tuyển |
| `deadline` | `DATE NULL` | NULL = không hạn; hết hạn sau cuối ngày `deadline` |
| `description` | `MEDIUMTEXT NOT NULL` | Markdown |
| `status` | `VARCHAR(20) NOT NULL` | `OPEN` \| `CLOSED` |
| `is_deleted` | | |

### 2.3 `job_posting_stores`
PK (`job_posting_id`, `store_id`), FK tương ứng. **Không có dòng nào = tuyển toàn chuỗi** (mọi cơ sở đang hoạt động).

### 2.4 `job_applications`
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `job_posting_id` | `BIGINT NOT NULL` FK | |
| `store_id` | `BIGINT NOT NULL` FK → `stores.id` | cơ sở ứng viên muốn làm; phải thuộc tập cơ sở của tin |
| `full_name` | `VARCHAR(100) NOT NULL` | |
| `phone` | `VARCHAR(20) NOT NULL` | |
| `email` | `VARCHAR(150) NULL` | |
| `message` | `VARCHAR(2000) NULL` | |
| `cv_file_key` | `VARCHAR(100) NULL` | tên file ngẫu nhiên trong thư mục riêng |
| `cv_original_name` | `VARCHAR(255) NULL` | |
| `cv_content_type` | `VARCHAR(100) NULL` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'NEW'` | `NEW` \| `CONTACTED` \| `HIRED` \| `REJECTED` |
| `internal_note` | `VARCHAR(2000) NULL` | |
| `handled_by` | `BIGINT NULL` FK → `users.id` | |
| `handled_at` | `DATETIME NULL` | |
| `client_ip` | `VARCHAR(45) NULL` | phục vụ chống spam |

Chỉ mục: (`store_id`, `status`), (`job_posting_id`, `phone`, `created_at`).

### 2.5 `feedbacks`
| Cột | Kiểu | Ghi chú |
|---|---|---|
| `type` | `VARCHAR(20) NOT NULL` | `SUGGESTION` \| `COMPLAINT` \| `PARTNERSHIP` \| `OTHER` |
| `store_id` | `BIGINT NULL` FK | NULL = chung toàn chuỗi |
| `order_id` | `BIGINT NULL` FK → `orders.id` | |
| `user_id` | `BIGINT NULL` FK → `users.id` | người gửi đã đăng nhập |
| `full_name` | `VARCHAR(100) NOT NULL` | |
| `phone` | `VARCHAR(20) NULL` | |
| `email` | `VARCHAR(150) NULL` | phải có ít nhất một trong `phone`/`email` |
| `subject` | `VARCHAR(200) NOT NULL` | |
| `content` | `VARCHAR(5000) NOT NULL` | |
| `status` | `VARCHAR(20) NOT NULL DEFAULT 'NEW'` | `NEW` \| `IN_PROGRESS` \| `RESOLVED` |
| `resolution_note` | `VARCHAR(2000) NULL` | |
| `handled_by`, `handled_at`, `client_ip` | | như trên |

Chỉ mục: (`store_id`, `status`), (`status`, `created_at`).

## 3. Tin tức

### API
- Công khai: `GET /api/v1/news?page&size` (đã đăng, ghim trước rồi `published_at` giảm dần; trả tóm tắt, không trả `content`), `GET /api/v1/news/{slug}` (chi tiết + 3 bài liên quan mới nhất khác bài này), `GET /api/v1/news/latest?limit=3`.
- ADMIN: `GET /api/v1/admin/news?status&keyword&page&size`, `GET /api/v1/admin/news/{id}` (xem cả nháp/hẹn giờ), `POST`, `PUT /{id}`, `DELETE /{id}` (xoá mềm).
- Bài chưa hiện với khách → 404.

### Ràng buộc
Tiêu đề 3–200 ký tự; nội dung không rỗng; `slug` chỉ `[a-z0-9-]`, trùng → tự thêm hậu tố; `PUBLISHED` mà không gửi `publishedAt` → server đặt `now`.

### Giao diện
- Khách: `/tin-tuc` (lưới thẻ: ảnh bìa, tiêu đề, ngày, tóm tắt; phân trang), `/tin-tuc/:slug` (bài + 3 bài liên quan), khối **"Tin mới"** trên trang chủ, link **Tin tức** ở header/footer.
- Admin: menu **Tin tức** — bảng lọc trạng thái (Nháp / Đã đăng / Hẹn giờ suy ra từ `published_at`), form soạn với thanh công cụ (**B**, *I*, H2, H3, danh sách, link, **chèn ảnh** qua tải ảnh), tab **Xem trước**, các ô slug, ảnh bìa, tóm tắt, ghim, trạng thái, thời điểm đăng (`datetime-local`, giờ Việt Nam, không đổi sang UTC).

### Hiển thị an toàn (D1)
Dùng `react-markdown` + `rehype-sanitize`, **không** bật HTML thô; link ngoài mở tab mới với `rel="noopener noreferrer"`. Component `MarkdownView` dùng chung cho tin tức và tuyển dụng.

## 4. Tuyển dụng

### API
- Công khai: `GET /api/v1/jobs?storeId` (tin `OPEN`, chưa hết hạn, chưa xoá; lọc tin tuyển ở cơ sở đó hoặc toàn chuỗi), `GET /api/v1/jobs/{slug}` (kèm danh sách cơ sở nhận hồ sơ; tin đóng/hết hạn vẫn xem được, cờ `acceptingApplications=false`), `POST /api/v1/jobs/{slug}/applications` (**multipart**: các trường + `cv` tuỳ chọn + `website` là ô bẫy bot).
- ADMIN: CRUD `…/admin/jobs`, đóng/mở tin.
- ADMIN + MANAGER: `GET /api/v1/job-applications?jobId&storeId&status&page&size` (MANAGER bị ép `storeId` = cơ sở mình, giống A), `GET /{id}`, `PATCH /{id}` (`status`, `internalNote`), `GET /{id}/cv` (tải file), `GET /count-new`.

### Quy tắc nộp hồ sơ
- Tin phải `OPEN` và chưa quá hạn → nếu không, 400 "Tin tuyển dụng đã hết hạn nhận hồ sơ".
- `storeId` phải thuộc tập cơ sở của tin (hoặc mọi cơ sở đang hoạt động nếu tuyển toàn chuỗi).
- Họ tên, SĐT (định dạng VN như phần đăng ký) bắt buộc; email đúng định dạng nếu có.
- Ô bẫy `website` có giá trị → trả **200 giả** (không lưu) để bot không biết bị chặn.
- **Chống spam:** cùng `phone` + cùng tin trong 24 giờ → 400 "Bạn đã nộp hồ sơ cho vị trí này"; mỗi IP tối đa **5 lần/giờ** (dùng chung bộ đếm với phản hồi, cài trong bộ nhớ, cửa sổ trượt) → 429 "Bạn thao tác quá nhanh, vui lòng thử lại sau".
- Có email → gửi email xác nhận (lỗi gửi chỉ ghi log).

### Lưu CV (bảo mật)
- Thư mục riêng `private-uploads/cv/` (cấu hình `app.private-upload-dir`), **không** map ra `/uploads/**`, được thêm vào `.gitignore`.
- Tên file = UUID; lưu `cv_original_name`, `cv_content_type`.
- Kiểm tra: ≤ 5MB; **chữ ký nội dung** (PDF `%PDF`, PNG, JPEG) khớp đuôi và loại khai báo — không khớp → 400 "File CV phải là PDF, JPG hoặc PNG".
- Tải CV: chỉ qua `GET /job-applications/{id}/cv` sau kiểm quyền; ngoài phạm vi → **404** (như A). Header `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`.
- Xoá mềm tin tuyển không xoá hồ sơ/CV.

### Giao diện
- Khách: `/tuyen-dung` (thẻ tin: vị trí, hình thức, lương, cơ sở, hạn nộp; lọc cơ sở), `/tuyen-dung/:slug` (mô tả Markdown + form ứng tuyển, ô chọn cơ sở, chọn file CV; tin hết hạn ẩn form). Link **Tuyển dụng** ở header/footer.
- Admin: menu **Tuyển dụng** — tab *Tin tuyển dụng* (bảng + form: vị trí, hình thức, lương, số lượng, hạn nộp, cơ sở tuyển — chọn nhiều hoặc "Toàn chuỗi", mô tả Markdown, trạng thái) và tab *Hồ sơ*.
- MANAGER: menu **Hồ sơ ứng tuyển** (chỉ cơ sở mình).
- Màn hình hồ sơ: bảng lọc tin/cơ sở/trạng thái; ngăn chi tiết với nút **Tải CV**, đổi trạng thái, ghi chú nội bộ; huy hiệu số hồ sơ `NEW` trên menu.

## 5. Phản hồi

### API
- Công khai (đăng nhập tuỳ chọn): `POST /api/v1/feedbacks` (JSON, có ô bẫy `website`).
- Ô chọn đơn hàng của khách đăng nhập dùng API đơn hàng hiện có (`GET /api/v1/orders`) — không thêm API mới.
- ADMIN + MANAGER: `GET /api/v1/admin/feedbacks?type&storeId&status&page&size` (MANAGER ép `storeId` = cơ sở mình; phản hồi chung toàn chuỗi `store_id NULL` chỉ ADMIN thấy), `GET /{id}`, `PATCH /{id}` (`status`, `resolutionNote`), `GET /count-new`.

### Quy tắc
- Có `orderId`: người gửi **phải đăng nhập và là chủ đơn** → nếu không, 404 "Không tìm thấy đơn hàng". Khi có đơn, `store_id` lấy theo **cơ sở của đơn** (bỏ qua `storeId` client gửi).
- Đăng nhập: `user_id` = người gửi; họ tên/SĐT/email mặc định từ tài khoản (sửa được).
- Bắt buộc: loại, họ tên, tiêu đề, nội dung (10–5000 ký tự), ít nhất một trong SĐT/email; `storeId` (nếu có) phải là cơ sở đang hoạt động.
- Chống spam: ô bẫy + giới hạn IP chung 5 lần/giờ (mục 4).
- Mỗi phản hồi mới: gửi email tới `contactEmail` trong cấu hình trang web (nếu có), lỗi gửi chỉ ghi log, không ảnh hưởng kết quả.
- Đổi trạng thái sang `IN_PROGRESS`/`RESOLVED` ghi `handled_by`, `handled_at`.

### Giao diện
- Trang **Liên hệ**: giữ khối hotline/email, thêm **form phản hồi** (loại, cơ sở, đơn hàng liên quan nếu đăng nhập, họ tên, SĐT, email, tiêu đề, nội dung). Chọn đơn → tự điền cơ sở và khoá ô cơ sở.
- Trang theo dõi đơn: nút **"Phản hồi về đơn này"** → `/contact?orderCode=…` (trang Liên hệ hiện có, form mở sẵn đơn).
- Admin: menu **Phản hồi**; MANAGER: menu **Phản hồi** (cơ sở mình). Bảng lọc loại/cơ sở/trạng thái, ngăn chi tiết (link sang đơn hàng nếu có), đổi trạng thái, ghi chú xử lý, huy hiệu số phản hồi `NEW`.

## 6. Phân quyền tổng hợp

| Hành động | Khách | CUSTOMER | STAFF/SHIPPER | MANAGER | ADMIN |
|---|---|---|---|---|---|
| Xem tin tức, tin tuyển | ✔ | ✔ | ✔ | ✔ | ✔ |
| Soạn tin tức / tin tuyển | | | | | ✔ |
| Nộp hồ sơ ứng tuyển | ✔ | ✔ | ✔ | ✔ | ✔ |
| Xem/xử lý hồ sơ, tải CV | | | | cơ sở mình | tất cả |
| Gửi phản hồi | ✔ (không gắn đơn) | ✔ (đơn của mình) | ✔ | ✔ | ✔ |
| Xem/xử lý phản hồi | | | | cơ sở mình | tất cả |

Truy cập ngoài phạm vi trả 404 như A.

## 7. Kiểm thử
- Unit service: slug (bỏ dấu, trùng → hậu tố), hiển thị bài theo trạng thái/`published_at` với Clock cố định; hạn nộp tin tuyển; ràng buộc cơ sở của hồ sơ; quy tắc gắn đơn phản hồi.
- Bảo mật: MANAGER không xem/tải được hồ sơ, CV, phản hồi của cơ sở khác (404); khách không gắn được đơn người khác; STAFF/CUSTOMER bị chặn API quản trị; bài nháp/hẹn giờ trả 404 với khách.
- File CV: PDF/PNG/JPEG hợp lệ; file đổi đuôi (vd `.exe` → `.pdf`) bị chặn; > 5MB bị chặn; CV không lộ qua `/uploads/**`.
- Chống spam: ô bẫy trả 200 không lưu; nộp trùng SĐT trong 24h → 400; quá 5 lần/giờ/IP → 429.
- Migration V15 trên bản sao DB; smoke HTTP; chụp giao diện bằng Chrome.

## 8. Rủi ro
- **Bộ đếm chống spam trong bộ nhớ** mất khi khởi động lại và không chia sẻ giữa nhiều máy chủ — đủ cho quy mô hiện tại; mở rộng sau bằng Redis.
- **CV là dữ liệu cá nhân:** thư mục riêng phải được sao lưu cùng DB khi triển khai và không đưa vào git; ghi chú trong `SETUP.md`.
- **Slug tiếng Việt:** bỏ dấu cả `đ/Đ` → `d`.
