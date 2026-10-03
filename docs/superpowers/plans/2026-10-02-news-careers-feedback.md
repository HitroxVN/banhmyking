# Tin tức, Tuyển dụng, Phản hồi (dự án con D) — Kế hoạch triển khai

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thêm 3 mô-đun nội dung độc lập — tin tức (Markdown, hẹn giờ, ghim), tuyển dụng theo cơ sở (form công khai + CV lưu riêng tư), phản hồi gắn cơ sở/đơn hàng có trạng thái xử lý — cùng giao diện khách, ADMIN và MANAGER.

**Architecture:** Migration V15 tạo 5 bảng (`news_posts`, `job_postings`, `job_posting_stores`, `job_applications`, `feedbacks`). Mỗi mô-đun có entity + repository + một service cụ thể (inject `Clock`) + controller công khai / quản trị; phạm vi cơ sở đi qua `StoreAccessGuard` của A (ngoài phạm vi → 404). Hai tiện ích dùng chung: `SubmissionRateLimiter` (bộ đếm IP trong bộ nhớ, cửa sổ trượt 5 lần/giờ, chung cho hồ sơ + phản hồi) và `CvStorageService` (thư mục `private-uploads/cv/`, kiểm chữ ký file, chỉ tải qua API có kiểm quyền). Frontend hiển thị Markdown bằng `react-markdown` + `rehype-sanitize` qua một component `MarkdownView` dùng chung.

**Tech Stack:** Java 17, Spring Boot 4.1, Hibernate 7 (`ddl-auto=validate`), Flyway, MariaDB 10.4 (XAMPP cổng 3307), JUnit 5 + Mockito + AssertJ + MockMvc; React 19 + TypeScript + Vite, oxlint, `react-markdown` 10.1.0, `rehype-sanitize` 6.0.0.

**Spec:** `docs/superpowers/specs/2026-10-02-news-careers-feedback-design.md`

## Global Constraints

- **Agent KHÔNG chạy `git add/commit/push/stash/checkout/reset`.** Mỗi bước "Commit (người dùng tự chạy)" chỉ là gợi ý lệnh để **người dùng** tự chạy; message tiếng Việt, **không** thêm dòng Co-Authored-By.
- Làm trên nhánh `feature/news-careers` (tạo từ `feature/combo-sale`).
- File Java của repo dùng CRLF: sửa bằng công cụ Edit/Write, **không dùng `sed -i`**. File mới viết bằng Write được chấp nhận (`core.autocrlf=true` chuẩn hoá khi commit).
- `spring.jpa.hibernate.ddl-auto=validate`: entity phải khớp migration (tên cột, kiểu, có/không có cột).
- **Chỉ một migration mới: `V15__news_careers_feedback.sql`.** Không sửa V1–V14. SQL chạy được trên **MariaDB 10.4** (và MySQL 8), chỉ tạo bảng mới nên dữ liệu cũ giữ nguyên hợp lệ.
- Lỗi nghiệp vụ: `throw new BusinessException(ErrorCode.VALIDATION_ERROR | BUSINESS_ERROR, "<tiếng Việt>")` (→ 400); không tìm thấy / ngoài phạm vi → `ResourceNotFoundException` (404, như A); chống spam → `ErrorCode.TOO_MANY_REQUESTS` (429, thêm ở Task 2). Thông báo tiếng Việt, code tiếng Anh.
- Thời gian: inject `java.time.Clock` (bean `config/TimeConfig`, múi giờ `TimeConfig.VIETNAM`) cho mọi logic "bây giờ" trong code mới. Không gọi `LocalDateTime.now()` / `LocalDate.now()` / `Instant.now()` không kèm clock.
- Phạm vi cơ sở (spec §6): MANAGER chỉ thấy/xử lý hồ sơ và phản hồi của cơ sở mình (phản hồi `store_id NULL` chỉ ADMIN thấy); truy cập ngoài phạm vi → **404**. Chỉ ADMIN soạn tin tức / tin tuyển dụng.
- Test tích hợp chạm DB dev phải **tự tạo dữ liệu và rollback** (`@Transactional`), **không** giả định DB rỗng, **không** giả định có cơ sở mã `CS01`.
- Không thêm dependency Maven. Bộ đếm chống spam là component trong bộ nhớ.
- npm: chỉ được thêm `react-markdown@10.1.0` và `rehype-sanitize@6.0.0` (cài `--save-exact`, tương thích React 19). Không thêm package nào khác.
- `private-uploads/` phải nằm trong `.gitignore`; CV **không bao giờ** được phục vụ qua `/uploads/**`.
- Lệnh test backend (Git Bash, thư mục gốc, **luôn `clean`**): `./mvnw -B clean test -Dtest=<TênClass>`; toàn bộ: `./mvnw -B clean test` (cần MariaDB XAMPP cổng 3307 đang chạy cho test `@SpringBootTest`).
- Cổng kiểm frontend (không có framework test FE), trong `frontend/`: `npx tsc -b` (0 lỗi), `npm run build` (`✓ built`), `npx oxlint src` (không thêm cảnh báo mới; mốc hiện tại **26** cảnh báo).
- Ngoài phạm vi (KHÔNG làm): trả lời khách qua email trong trang quản trị, bình luận bài viết, danh mục/thẻ tin, MANAGER tự đăng tin tuyển, reCAPTCHA, đa ngôn ngữ.

## Làm rõ spec (chốt khi lập kế hoạch)

1. Service mới là **class cụ thể** (không tách interface/impl), theo mẫu `FileStorageService`/`ProductPricing`: `NewsService`, `JobPostingService`, `CvStorageService`, `JobApplicationService`, `FeedbackService`.
2. API gửi phản hồi nhận **`orderCode`** (mã đơn khách nhìn thấy) thay cho `orderId`; DB vẫn lưu `feedbacks.order_id`. Kiểm chủ đơn bằng `OrderRepository.findByOrderCodeAndUserId` có sẵn. Trang theo dõi đơn mở `/contact?orderCode=…` đúng như spec.
3. Slug: admin nhập slug thì được **chuẩn hoá** bằng `SlugUtils.slugify` (không từ chối chữ hoa/dấu); kết quả rỗng → 400. Bỏ trống khi tạo → sinh từ tiêu đề (tiêu đề không ra ký tự nào → `tin-tuc` / `tuyen-dung`). Bỏ trống khi sửa → **giữ slug cũ** (URL ổn định). Trùng (kể cả bản ghi đã xoá mềm, vì UNIQUE phủ mọi dòng) → `-2`, `-3`…
4. Lọc trạng thái tin tức phía admin: tham số `status` nhận `DRAFT | SCHEDULED | PUBLISHED` (enum `NewsDisplayState`, suy ra từ `status` + `published_at` so với Clock).
5. Kiểm dữ liệu nhập ở **service** (không dùng `@Valid`) để mọi lỗi là một câu tiếng Việt cụ thể; form multipart nhận các trường bằng `@RequestParam(required = false)` (thiếu trường không thành 500).
6. Bộ đếm IP (5 lần/giờ, cửa sổ trượt, **chung** cho hồ sơ + phản hồi): `check` trước khi xử lý (→ 429), `record` **chỉ sau khi lưu thành công**; ô bẫy không tính. IP = `request.getRemoteAddr()`; chỉ đọc `X-Forwarded-For` khi `app.trust-forwarded-for=true` (mặc định `false`, chống giả mạo header).
7. Nộp hồ sơ / phản hồi thật và bị ô bẫy chặn đều trả **200 cùng message, không kèm id** — bot không phân biệt được.
8. Người gửi phản hồi đã đăng nhập: server tự lấy **họ tên và email** tài khoản khi ô để trống; SĐT không tự lấy (SĐT cũ trong `users` có thể sai định dạng). Frontend điền sẵn cả ba ô.
9. "Hết hạn sau cuối ngày `deadline`": còn nhận hồ sơ khi `LocalDate.now(clock) ≤ deadline`. Hạn nộp ở quá khứ bị chặn khi tạo tin hoặc khi đổi hạn.
10. Nộp trùng "cùng SĐT + cùng tin trong 24 giờ" so với `created_at` (auditing ghi theo giờ JVM — trên máy chủ hiện tại trùng giờ Việt Nam, giống các báo cáo đang có).
11. Đổi trạng thái hồ sơ / phản hồi sang giá trị khác `NEW` ghi `handled_by`, `handled_at`; quay về `NEW` giữ nguyên người xử lý cũ.
12. Bộ lọc "tin tuyển dụng" ở màn hồ sơ: ADMIN lấy từ `/admin/jobs`, MANAGER lấy từ API công khai `/jobs` (chỉ tin đang mở) vì `/admin/jobs` chỉ dành cho ADMIN.
13. "Link sang đơn hàng" trong phản hồi: nút **Xem đơn** mở khung tóm tắt ngay trong ngăn chi tiết bằng `GET /api/v1/orders/{code}` (API hiện có đã cho STAFF/MANAGER cơ sở mình và ADMIN), vì trang đơn admin chưa có tìm theo mã.
14. Huy hiệu số `NEW` trên menu: hỏi `count-new` mỗi 60 giây + ngay khi trang xử lý phát sự kiện `bmk:inbox-changed`. Số hồ sơ `NEW` gắn vào menu **Tuyển dụng** (ADMIN) và **Hồ sơ ứng tuyển** (MANAGER).
15. Ảnh bìa và ảnh chèn trong bài/tin tuyển dùng `POST /api/v1/catalog/products/upload-image` (ADMIN, đã có) — ảnh công khai trong `uploads/products/`.
16. CV: `cv_content_type` lưu loại **suy ra từ chữ ký file** (không tin giá trị client). File 5–10MB → 400 "File CV không được vượt quá 5MB"; > 10MB bị chặn bởi giới hạn multipart hiện có → 413.
17. Email (xác nhận ứng tuyển, báo phản hồi mới) gửi đồng bộ sau khi lưu, giống đăng ký; `EmailServiceImpl.send` đã nuốt lỗi SMTP và chỉ ghi log.
18. Entity `Feedback` đặt tên field `relatedOrder` (cột `order_id`) để tránh từ khoá `order` trong JPQL.
19. Matcher URL của Spring Security cho toàn bộ API mới được thêm ở **Task 9** (cùng test bảo mật đầu-cuối). Task 3–8 kiểm controller bằng MockMvc standalone (mẫu `OrderControllerTest`), không qua filter chain.

## Bản đồ file

Gốc backend: `M = src/main/java/com/banhmyking/banhmyking/`, `T = src/test/java/com/banhmyking/banhmyking/`.

**Backend — tạo mới**

| File | Trách nhiệm |
|---|---|
| `src/main/resources/db/migration/V15__news_careers_feedback.sql` | 5 bảng mới |
| `M/enums/NewsStatus.java`, `NewsDisplayState.java`, `EmploymentType.java`, `JobStatus.java`, `ApplicationStatus.java`, `FeedbackType.java`, `FeedbackStatus.java` | Enum trạng thái/loại |
| `M/entity/NewsPost.java`, `JobPosting.java`, `JobApplication.java`, `Feedback.java` | Entity |
| `M/repository/NewsPostRepository.java`, `JobPostingRepository.java`, `JobApplicationRepository.java`, `FeedbackRepository.java` | Truy vấn hiển thị/lọc/đếm |
| `M/util/SlugUtils.java` | Bỏ dấu → slug, thêm hậu tố khi trùng |
| `M/util/ContactFields.java` | Chuẩn hoá + kiểm họ tên, SĐT VN, email, độ dài |
| `M/security/SubmissionRateLimiter.java` | Bộ đếm IP 5 lần/giờ trong bộ nhớ |
| `M/security/ClientIpResolver.java` | Lấy IP client |
| `M/service/NewsService.java` | Tin tức công khai + quản trị |
| `M/service/JobPostingService.java` | Tin tuyển dụng công khai + quản trị, cơ sở nhận hồ sơ |
| `M/service/CvStorageService.java` | Lưu/đọc CV trong `private-uploads/cv/` |
| `M/service/JobApplicationService.java` | Nộp hồ sơ + xử lý hồ sơ theo phạm vi |
| `M/service/FeedbackService.java` | Gửi phản hồi + xử lý theo phạm vi + email |
| `M/dto/news/*`, `M/dto/job/*`, `M/dto/feedback/*` | Request/response |
| `M/controller/NewsController.java`, `AdminNewsController.java`, `JobController.java`, `AdminJobController.java`, `JobApplicationController.java`, `FeedbackController.java`, `AdminFeedbackController.java` | API |

**Backend — sửa:** `M/exception/ErrorCode.java` (thêm `TOO_MANY_REQUESTS`), `M/service/EmailService.java`, `M/service/impl/EmailServiceImpl.java`, `M/config/SecurityConfig.java`, `src/main/resources/application.properties`, `.gitignore`.

**Frontend — tạo mới** (gốc `frontend/src/`): `types/content.ts`, `utils/contentLabels.ts`, `api/newsApi.ts`, `api/jobApi.ts`, `api/feedbackApi.ts`, `hooks/useInboxCounts.ts`, `components/content/MarkdownView.tsx`, `components/content/MarkdownEditor.tsx`, `components/home/LatestNewsSection.tsx`, `components/careers/ApplyForm.tsx`, `components/careers/JobApplicationsPanel.tsx`, `components/feedback/FeedbackForm.tsx`, `pages/news/NewsListPage.tsx`, `pages/news/NewsDetailPage.tsx`, `pages/careers/JobsPage.tsx`, `pages/careers/JobDetailPage.tsx`, `pages/admin/AdminNewsPage.tsx`, `pages/admin/AdminJobsPage.tsx`, `pages/manager/ManagerApplicationsPage.tsx`, `pages/inbox/FeedbackInboxPage.tsx`, `styles/components/content.css`.

**Frontend — sửa:** `package.json`/`package-lock.json`, `App.tsx`, `components/layout/navItems.ts`, `components/layout/DashboardLayout.tsx`, `components/layout/CustomerLayout.tsx`, `pages/LandingPage.tsx`, `pages/static/StaticPages.tsx`, `pages/OrderTrackingPage.tsx`, `styles/layout.css`.

---

## Task 1: Schema V15 + enum + entity + repository

**Files:**
- Create: `src/main/resources/db/migration/V15__news_careers_feedback.sql`
- Create: `M/enums/NewsStatus.java`, `M/enums/NewsDisplayState.java`, `M/enums/EmploymentType.java`, `M/enums/JobStatus.java`, `M/enums/ApplicationStatus.java`, `M/enums/FeedbackType.java`, `M/enums/FeedbackStatus.java`
- Create: `M/entity/NewsPost.java`, `M/entity/JobPosting.java`, `M/entity/JobApplication.java`, `M/entity/Feedback.java`
- Create: `M/repository/NewsPostRepository.java`, `M/repository/JobPostingRepository.java`, `M/repository/JobApplicationRepository.java`, `M/repository/FeedbackRepository.java`
- Test: `T/repository/ContentSchemaIntegrationTest.java`

**Interfaces:**
- Consumes: `BaseEntity` (id, createdAt, updatedAt), entity `Store`, `User`, `Order`; repository `StoreRepository`, `UserRepository`, `OrderRepository` (đã có).
- Produces:
  - `enum NewsStatus { DRAFT, PUBLISHED }`, `enum NewsDisplayState { DRAFT, SCHEDULED, PUBLISHED }`, `enum EmploymentType { FULL_TIME, PART_TIME, SEASONAL }`, `enum JobStatus { OPEN, CLOSED }`, `enum ApplicationStatus { NEW, CONTACTED, HIRED, REJECTED }`, `enum FeedbackStatus { NEW, IN_PROGRESS, RESOLVED }`, `enum FeedbackType { SUGGESTION, COMPLAINT, PARTNERSHIP, OTHER; String getLabel() }`.
  - `NewsPost`: getter/setter `title, slug, coverImageUrl, summary, content, status (NewsStatus, mặc định DRAFT), publishedAt (LocalDateTime), pinned (boolean → isPinned()), author (User), deleted (isDeleted())`; `boolean isVisibleAt(LocalDateTime now)`; `NewsDisplayState displayStateAt(LocalDateTime now)`.
  - `JobPosting`: `title, slug, employmentType, salaryText, headcount (Integer), deadline (LocalDate), description, status (JobStatus, mặc định OPEN), deleted, stores (Set<Store>)`; `boolean isChainWide()`, `boolean acceptsApplicationsOn(LocalDate today)`, `boolean isExpiredOn(LocalDate today)`.
  - `JobApplication`: `jobPosting, store, fullName, phone, email, message, cvFileKey, cvOriginalName, cvContentType, status (mặc định NEW), internalNote, handledBy (User), handledAt, clientIp`; `boolean hasCv()`.
  - `Feedback`: `type, store (nullable), relatedOrder (Order, nullable), user (nullable), fullName, phone, email, subject, content, status (mặc định NEW), resolutionNote, handledBy, handledAt, clientIp`.
  - `NewsPostRepository`: `findByIdAndDeletedFalse(Long)`, `findBySlugAndDeletedFalse(String)`, `existsBySlug(String)`, `existsBySlugAndIdNot(String, Long)`, `Page<NewsPost> findVisible(LocalDateTime now, Pageable)`, `List<NewsPost> findVisibleExcluding(LocalDateTime now, Long excludeId, Pageable)`, `Page<NewsPost> searchAdmin(NewsStatus status, LocalDateTime publishedAfter, LocalDateTime publishedUntil, String keyword, Pageable)`.
  - `JobPostingRepository`: `findByIdAndDeletedFalse(Long)`, `findBySlugAndDeletedFalse(String)`, `existsBySlug(String)`, `existsBySlugAndIdNot(String, Long)`, `List<JobPosting> findOpen(LocalDate today, Long storeId)`, `Page<JobPosting> searchAdmin(JobStatus status, String keyword, Pageable)`.
  - `JobApplicationRepository`: `existsByJobPostingIdAndPhoneAndCreatedAtAfter(Long, String, LocalDateTime)`, `Page<JobApplication> search(Long jobId, Long storeId, ApplicationStatus status, Pageable)`, `long countByStatusInScope(ApplicationStatus status, Long storeId)`.
  - `FeedbackRepository`: `Page<Feedback> search(FeedbackType type, Long storeId, FeedbackStatus status, Pageable)`, `long countByStatusInScope(FeedbackStatus status, Long storeId)`.

- [ ] **Step 1: Viết test tích hợp (đỏ)**

`T/repository/ContentSchemaIntegrationTest.java`:

```java
package com.banhmyking.banhmyking.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * V15 trên DB dev thật — mọi dữ liệu do test tự tạo và rollback sau test.
 * Không giả định DB rỗng: bài "đã đăng" đặt ở năm 2099 và mọi khẳng định lọc theo id do test tạo.
 */
@SpringBootTest
@Transactional
class ContentSchemaIntegrationTest {

    private static final LocalDateTime FAR_NOW = LocalDateTime.of(2100, 1, 1, 0, 0);

    @Autowired private NewsPostRepository newsPostRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void newsVisibilityFollowsStatusPublishedAtAndSoftDelete() {
        String tag = tag();
        NewsPost published = newsPostRepository.save(news("pub-" + tag, NewsStatus.PUBLISHED,
                LocalDateTime.of(2099, 12, 31, 10, 0)));
        NewsPost scheduled = newsPostRepository.save(news("sch-" + tag, NewsStatus.PUBLISHED,
                LocalDateTime.of(2100, 6, 1, 8, 0)));
        NewsPost draft = newsPostRepository.save(news("dra-" + tag, NewsStatus.DRAFT, null));
        NewsPost deleted = news("del-" + tag, NewsStatus.PUBLISHED, LocalDateTime.of(2099, 12, 31, 11, 0));
        deleted.setDeleted(true);
        deleted = newsPostRepository.save(deleted);
        entityManager.flush();
        entityManager.clear();

        List<Long> visible = newsPostRepository
                .findVisible(FAR_NOW, PageRequest.of(0, 50, Sort.by(Sort.Order.desc("publishedAt"))))
                .getContent().stream().map(NewsPost::getId).toList();
        assertThat(visible).contains(published.getId())
                .doesNotContain(scheduled.getId(), draft.getId(), deleted.getId());
        assertThat(visible.get(0)).isEqualTo(published.getId());

        assertThat(newsPostRepository.findVisibleExcluding(FAR_NOW, published.getId(), PageRequest.of(0, 50)))
                .extracting(NewsPost::getId).doesNotContain(published.getId());
        assertThat(newsPostRepository.findBySlugAndDeletedFalse("del-" + tag)).isEmpty();
        assertThat(newsPostRepository.existsBySlug("del-" + tag)).isTrue();
        assertThat(newsPostRepository.existsBySlugAndIdNot("pub-" + tag, published.getId())).isFalse();

        assertThat(newsPostRepository.searchAdmin(NewsStatus.PUBLISHED, FAR_NOW, null, tag, PageRequest.of(0, 10))
                .getContent()).extracting(NewsPost::getId).containsExactly(scheduled.getId());
        assertThat(newsPostRepository.searchAdmin(NewsStatus.PUBLISHED, null, FAR_NOW, tag, PageRequest.of(0, 10))
                .getContent()).extracting(NewsPost::getId).containsExactly(published.getId());
        assertThat(newsPostRepository.searchAdmin(NewsStatus.DRAFT, null, null, tag.toLowerCase(), PageRequest.of(0, 10))
                .getContent()).extracting(NewsPost::getId).containsExactly(draft.getId());

        NewsPost reloaded = newsPostRepository.findById(published.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("# Nội dung\n\n**Đậm** và _nghiêng_");
        assertThat(reloaded.isPinned()).isTrue();
        assertThat(reloaded.isVisibleAt(FAR_NOW)).isTrue();
        assertThat(reloaded.isVisibleAt(LocalDateTime.of(2099, 12, 31, 9, 59))).isFalse();
    }

    @Test
    void newsSlugIsUniqueAcrossAllRows() {
        String tag = tag();
        newsPostRepository.saveAndFlush(news("dup-" + tag, NewsStatus.DRAFT, null));
        assertThatThrownBy(() -> newsPostRepository.saveAndFlush(news("dup-" + tag, NewsStatus.DRAFT, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void openJobsFilterByStoreDeadlineStatusAndSoftDelete() {
        String tag = tag();
        Store storeA = storeRepository.save(store("JA" + tag));
        Store storeB = storeRepository.save(store("JB" + tag));
        LocalDate today = LocalDate.of(2026, 10, 2);
        JobPosting onlyA = jobPostingRepository.save(job("a-" + tag, JobStatus.OPEN, null, storeA));
        JobPosting chainWide = jobPostingRepository.save(job("chain-" + tag, JobStatus.OPEN, today));
        JobPosting onlyB = jobPostingRepository.save(job("b-" + tag, JobStatus.OPEN, null, storeB));
        JobPosting closed = jobPostingRepository.save(job("closed-" + tag, JobStatus.CLOSED, null, storeA));
        JobPosting expired = jobPostingRepository.save(job("exp-" + tag, JobStatus.OPEN, today.minusDays(1), storeA));
        JobPosting deleted = job("del-" + tag, JobStatus.OPEN, null, storeA);
        deleted.setDeleted(true);
        deleted = jobPostingRepository.save(deleted);
        entityManager.flush();
        entityManager.clear();

        Set<Long> mine = Set.of(onlyA.getId(), chainWide.getId(), onlyB.getId(), closed.getId(),
                expired.getId(), deleted.getId());
        assertThat(jobPostingRepository.findOpen(today, storeA.getId()).stream()
                .map(JobPosting::getId).filter(mine::contains).toList())
                .containsExactlyInAnyOrder(onlyA.getId(), chainWide.getId());
        assertThat(jobPostingRepository.findOpen(today, null).stream()
                .map(JobPosting::getId).filter(mine::contains).toList())
                .containsExactlyInAnyOrder(onlyA.getId(), chainWide.getId(), onlyB.getId());

        JobPosting loadedA = jobPostingRepository.findById(onlyA.getId()).orElseThrow();
        assertThat(loadedA.getStores()).extracting(Store::getId).containsExactly(storeA.getId());
        assertThat(loadedA.getEmploymentType()).isEqualTo(EmploymentType.PART_TIME);
        assertThat(jobPostingRepository.findById(chainWide.getId()).orElseThrow().isChainWide()).isTrue();
        assertThat(jobPostingRepository.findBySlugAndDeletedFalse("del-" + tag)).isEmpty();
        assertThat(jobPostingRepository.searchAdmin(JobStatus.CLOSED, tag.toLowerCase(), PageRequest.of(0, 10))
                .getContent()).extracting(JobPosting::getId).containsExactly(closed.getId());
    }

    @Test
    void applicationsAndFeedbacksPersistAndFilterByStore() {
        String tag = tag();
        Store storeA = storeRepository.save(store("FA" + tag));
        Store storeB = storeRepository.save(store("FB" + tag));
        User admin = userRepository.save(user(tag, "adm", RoleName.ADMIN));
        User customer = userRepository.save(user(tag, "cus", RoleName.CUSTOMER));
        JobPosting job = jobPostingRepository.save(job("app-" + tag, JobStatus.OPEN, null));

        JobApplication inA = application(job, storeA, "0901234567");
        inA.setCvFileKey("0123456789abcdef0123456789abcdef.pdf");
        inA.setCvOriginalName("CV Nguyễn Văn A.pdf");
        inA.setCvContentType("application/pdf");
        inA.setClientIp("10.0.0.1");
        inA = jobApplicationRepository.save(inA);
        JobApplication inB = jobApplicationRepository.save(application(job, storeB, "0912345678"));
        entityManager.flush();

        assertThat(jobApplicationRepository.existsByJobPostingIdAndPhoneAndCreatedAtAfter(
                job.getId(), "0901234567", LocalDateTime.of(2000, 1, 1, 0, 0))).isTrue();
        assertThat(jobApplicationRepository.existsByJobPostingIdAndPhoneAndCreatedAtAfter(
                job.getId(), "0901234567", LocalDateTime.of(2999, 1, 1, 0, 0))).isFalse();
        assertThat(jobApplicationRepository.search(null, storeA.getId(), null, PageRequest.of(0, 10)).getContent())
                .extracting(JobApplication::getId).containsExactly(inA.getId());
        assertThat(jobApplicationRepository.search(job.getId(), null, ApplicationStatus.NEW, PageRequest.of(0, 10))
                .getContent()).extracting(JobApplication::getId).containsExactlyInAnyOrder(inA.getId(), inB.getId());
        assertThat(jobApplicationRepository.countByStatusInScope(ApplicationStatus.NEW, storeA.getId())).isEqualTo(1);

        Order order = orderRepository.save(order(tag, customer, storeB));
        Feedback withOrder = feedback(FeedbackType.COMPLAINT, storeB, "Bánh giao tới bị nguội");
        withOrder.setRelatedOrder(order);
        withOrder.setUser(customer);
        withOrder = feedbackRepository.save(withOrder);
        Feedback chainWide = feedbackRepository.save(feedback(FeedbackType.SUGGESTION, null, "Thêm món chay"));
        Feedback resolvedInA = feedback(FeedbackType.OTHER, storeA, "Hỏi giờ mở cửa");
        resolvedInA.setStatus(FeedbackStatus.RESOLVED);
        resolvedInA.setHandledBy(admin);
        resolvedInA.setHandledAt(LocalDateTime.of(2026, 10, 2, 10, 0));
        resolvedInA = feedbackRepository.save(resolvedInA);
        entityManager.flush();
        entityManager.clear();

        assertThat(feedbackRepository.search(null, storeB.getId(), null, PageRequest.of(0, 10)).getContent())
                .extracting(Feedback::getId).containsExactly(withOrder.getId());
        Set<Long> mine = Set.of(withOrder.getId(), chainWide.getId(), resolvedInA.getId());
        assertThat(feedbackRepository.search(null, null, FeedbackStatus.NEW, PageRequest.of(0, 50)).getContent()
                .stream().map(Feedback::getId).filter(mine::contains).toList())
                .containsExactlyInAnyOrder(withOrder.getId(), chainWide.getId());
        assertThat(feedbackRepository.countByStatusInScope(FeedbackStatus.NEW, storeA.getId())).isZero();
        assertThat(feedbackRepository.countByStatusInScope(FeedbackStatus.NEW, storeB.getId())).isEqualTo(1);

        Feedback loaded = feedbackRepository.findById(withOrder.getId()).orElseThrow();
        assertThat(loaded.getRelatedOrder().getOrderCode()).isEqualTo("V15-" + tag);
        assertThat(loaded.getStatus()).isEqualTo(FeedbackStatus.NEW);
        assertThat(feedbackRepository.findById(resolvedInA.getId()).orElseThrow().getHandledBy().getId())
                .isEqualTo(admin.getId());
        JobApplication loadedApp = jobApplicationRepository.findById(inA.getId()).orElseThrow();
        assertThat(loadedApp.hasCv()).isTrue();
        assertThat(loadedApp.getStatus()).isEqualTo(ApplicationStatus.NEW);
        assertThat(jobApplicationRepository.findById(inB.getId()).orElseThrow().hasCv()).isFalse();
    }

    // ----------------------------------------------------------------------- helpers

    /** Chữ thường để slug/khoá so khớp chính xác (slug luôn là chữ thường). */
    private static String tag() {
        return Long.toString(System.nanoTime(), 36);
    }

    private static NewsPost news(String slug, NewsStatus status, LocalDateTime publishedAt) {
        NewsPost post = new NewsPost();
        post.setTitle("Tin " + slug);
        post.setSlug(slug.toLowerCase());
        post.setContent("# Nội dung\n\n**Đậm** và _nghiêng_");
        post.setStatus(status);
        post.setPublishedAt(publishedAt);
        post.setPinned(true);
        return post;
    }

    private static Store store(String code) {
        Store store = new Store();
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ thử nghiệm");
        return store;
    }

    private static JobPosting job(String slug, JobStatus status, LocalDate deadline, Store... stores) {
        JobPosting job = new JobPosting();
        job.setTitle("Phụ bếp " + slug);
        job.setSlug(slug.toLowerCase());
        job.setEmploymentType(EmploymentType.PART_TIME);
        job.setSalaryText("22–25k/giờ");
        job.setHeadcount(2);
        job.setDeadline(deadline);
        job.setDescription("## Mô tả\n- Phụ bếp ca tối");
        job.setStatus(status);
        job.getStores().addAll(List.of(stores));
        return job;
    }

    private static User user(String tag, String kind, RoleName role) {
        User user = new User();
        user.setEmail(kind + "-" + tag.toLowerCase() + "@test.local");
        user.setPassword("not-used");
        user.setFullName("V15 " + kind);
        user.setRole(role);
        return user;
    }

    private static JobApplication application(JobPosting job, Store store, String phone) {
        JobApplication app = new JobApplication();
        app.setJobPosting(job);
        app.setStore(store);
        app.setFullName("Ứng viên " + phone);
        app.setPhone(phone);
        return app;
    }

    private static Order order(String tag, User customer, Store store) {
        Order order = new Order();
        order.setOrderCode("V15-" + tag);
        order.setUser(customer);
        order.setStore(store);
        order.setStatus(OrderStatus.DELIVERED);
        order.setReceiverName("Khách thử");
        order.setReceiverPhone("0900000000");
        order.setShippingAddress("1 Đường Thử");
        order.setSubtotal(new BigDecimal("45000"));
        order.setTotal(new BigDecimal("45000"));
        return order;
    }

    private static Feedback feedback(FeedbackType type, Store store, String subject) {
        Feedback feedback = new Feedback();
        feedback.setType(type);
        feedback.setStore(store);
        feedback.setFullName("Người gửi thử");
        feedback.setPhone("0987654321");
        feedback.setSubject(subject);
        feedback.setContent("Nội dung phản hồi đủ dài để hợp lệ.");
        return feedback;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=ContentSchemaIntegrationTest`
Expected: COMPILATION ERROR (`NewsPost`, `JobPosting`, `JobApplication`, `Feedback`, các enum và repository chưa tồn tại).

- [ ] **Step 3: Migration `src/main/resources/db/migration/V15__news_careers_feedback.sql`**

```sql
-- V15: tin tức, tuyển dụng, phản hồi (spec docs/superpowers/specs/2026-10-02-news-careers-feedback-design.md §2)
-- Chỉ tạo bảng mới nên dữ liệu cũ giữ nguyên hợp lệ. Không dùng CHECK: ràng buộc nghiệp vụ
-- kiểm ở service để giữ thông báo tiếng Việt. Slug UNIQUE phủ cả dòng đã xoá mềm.

CREATE TABLE news_posts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(220) NOT NULL,
    cover_image_url VARCHAR(500) NULL,
    summary         VARCHAR(500) NULL,
    content         MEDIUMTEXT   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    published_at    DATETIME     NULL,
    pinned          BOOLEAN      NOT NULL DEFAULT FALSE,
    author_id       BIGINT       NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NULL,
    CONSTRAINT uk_news_posts_slug UNIQUE (slug),
    CONSTRAINT fk_news_posts_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_news_posts_visible (status, published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE job_postings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(220) NOT NULL,
    employment_type VARCHAR(20)  NOT NULL,
    salary_text     VARCHAR(100) NULL,
    headcount       INT          NULL,
    deadline        DATE         NULL,
    description     MEDIUMTEXT   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NULL,
    CONSTRAINT uk_job_postings_slug UNIQUE (slug),
    INDEX idx_job_postings_open (status, deadline)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Không có dòng nào cho một tin = tuyển toàn chuỗi (mọi cơ sở đang hoạt động).
CREATE TABLE job_posting_stores (
    job_posting_id BIGINT NOT NULL,
    store_id       BIGINT NOT NULL,
    PRIMARY KEY (job_posting_id, store_id),
    CONSTRAINT fk_jps_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id) ON DELETE CASCADE,
    CONSTRAINT fk_jps_store FOREIGN KEY (store_id) REFERENCES stores (id),
    INDEX idx_jps_store (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE job_applications (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_posting_id   BIGINT        NOT NULL,
    store_id         BIGINT        NOT NULL,
    full_name        VARCHAR(100)  NOT NULL,
    phone            VARCHAR(20)   NOT NULL,
    email            VARCHAR(150)  NULL,
    message          VARCHAR(2000) NULL,
    cv_file_key      VARCHAR(100)  NULL,
    cv_original_name VARCHAR(255)  NULL,
    cv_content_type  VARCHAR(100)  NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    internal_note    VARCHAR(2000) NULL,
    handled_by       BIGINT        NULL,
    handled_at       DATETIME      NULL,
    client_ip        VARCHAR(45)   NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NULL,
    CONSTRAINT fk_job_applications_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id),
    CONSTRAINT fk_job_applications_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_job_applications_handler FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_job_applications_store_status (store_id, status),
    INDEX idx_job_applications_dup (job_posting_id, phone, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE feedbacks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(20)   NOT NULL,
    store_id        BIGINT        NULL,
    order_id        BIGINT        NULL,
    user_id         BIGINT        NULL,
    full_name       VARCHAR(100)  NOT NULL,
    phone           VARCHAR(20)   NULL,
    email           VARCHAR(150)  NULL,
    subject         VARCHAR(200)  NOT NULL,
    content         VARCHAR(5000) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    resolution_note VARCHAR(2000) NULL,
    handled_by      BIGINT        NULL,
    handled_at      DATETIME      NULL,
    client_ip       VARCHAR(45)   NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NULL,
    CONSTRAINT fk_feedbacks_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_feedbacks_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_feedbacks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_feedbacks_handler FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_feedbacks_store_status (store_id, status),
    INDEX idx_feedbacks_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

- [ ] **Step 4: Bảy enum trong `M/enums/`**

`NewsStatus.java`:

```java
package com.banhmyking.banhmyking.enums;

/** Trạng thái lưu của bài tin tức (spec D §2.1). "Hẹn giờ" là PUBLISHED có published_at ở tương lai. */
public enum NewsStatus {
    DRAFT,
    PUBLISHED
}
```

`NewsDisplayState.java`:

```java
package com.banhmyking.banhmyking.enums;

/** Trạng thái hiển thị suy ra từ status + published_at so với Clock — dùng cho bộ lọc admin (spec D §3). */
public enum NewsDisplayState {
    DRAFT,
    SCHEDULED,
    PUBLISHED
}
```

`EmploymentType.java`:

```java
package com.banhmyking.banhmyking.enums;

/** Hình thức làm việc của tin tuyển dụng (spec D §2.2). */
public enum EmploymentType {
    FULL_TIME,
    PART_TIME,
    SEASONAL
}
```

`JobStatus.java`:

```java
package com.banhmyking.banhmyking.enums;

/** Tin tuyển dụng đang mở hay đã đóng (spec D §2.2). Hết hạn suy ra từ deadline, không phải trạng thái lưu. */
public enum JobStatus {
    OPEN,
    CLOSED
}
```

`ApplicationStatus.java`:

```java
package com.banhmyking.banhmyking.enums;

/** Trạng thái xử lý hồ sơ ứng tuyển (spec D §2.4). */
public enum ApplicationStatus {
    NEW,
    CONTACTED,
    HIRED,
    REJECTED
}
```

`FeedbackStatus.java`:

```java
package com.banhmyking.banhmyking.enums;

/** Trạng thái xử lý phản hồi (spec D §2.5). */
public enum FeedbackStatus {
    NEW,
    IN_PROGRESS,
    RESOLVED
}
```

`FeedbackType.java`:

```java
package com.banhmyking.banhmyking.enums;

/** Loại phản hồi (spec D §2.5). Nhãn tiếng Việt dùng trong email báo admin. */
public enum FeedbackType {
    SUGGESTION("Góp ý"),
    COMPLAINT("Khiếu nại"),
    PARTNERSHIP("Hợp tác"),
    OTHER("Khác");

    private final String label;

    FeedbackType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
```

- [ ] **Step 5: `M/entity/NewsPost.java`**

```java
package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Bài tin tức / khuyến mãi, nội dung Markdown (spec D §2.1). */
@Getter
@Setter
@Entity
@Table(name = "news_posts")
public class NewsPost extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(length = 500)
    private String summary;

    /** columnDefinition để ddl validate khớp MEDIUMTEXT (như SiteSetting dùng TEXT). */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsStatus status = NewsStatus.DRAFT;

    /** Giờ Việt Nam. Bắt buộc khi PUBLISHED; ở tương lai = hẹn giờ. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private boolean pinned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    /** Khách thấy bài khi PUBLISHED ∧ published_at ≤ now ∧ chưa xoá (spec D §2.1). */
    public boolean isVisibleAt(LocalDateTime now) {
        return !deleted && status == NewsStatus.PUBLISHED && publishedAt != null && !publishedAt.isAfter(now);
    }

    public NewsDisplayState displayStateAt(LocalDateTime now) {
        if (status == NewsStatus.DRAFT) {
            return NewsDisplayState.DRAFT;
        }
        return publishedAt != null && publishedAt.isAfter(now) ? NewsDisplayState.SCHEDULED : NewsDisplayState.PUBLISHED;
    }
}
```

- [ ] **Step 6: `M/entity/JobPosting.java`**

```java
package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/** Tin tuyển dụng (spec D §2.2–2.3). Không gắn cơ sở nào = tuyển toàn chuỗi. */
@Getter
@Setter
@Entity
@Table(name = "job_postings")
public class JobPosting extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Column(name = "salary_text", length = 100)
    private String salaryText;

    private Integer headcount;

    /** NULL = không hạn; hết hạn sau cuối ngày deadline. */
    private LocalDate deadline;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.OPEN;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "job_posting_stores",
            joinColumns = @JoinColumn(name = "job_posting_id"),
            inverseJoinColumns = @JoinColumn(name = "store_id"))
    private Set<Store> stores = new HashSet<>();

    public boolean isChainWide() {
        return stores.isEmpty();
    }

    public boolean isExpiredOn(LocalDate today) {
        return deadline != null && today.isAfter(deadline);
    }

    /** Còn nhận hồ sơ: OPEN, chưa xoá, chưa quá cuối ngày deadline (spec D §4). */
    public boolean acceptsApplicationsOn(LocalDate today) {
        return !deleted && status == JobStatus.OPEN && !isExpiredOn(today);
    }
}
```

- [ ] **Step 7: `M/entity/JobApplication.java`**

```java
package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Hồ sơ ứng tuyển (spec D §2.4). CV nằm trong thư mục riêng, chỉ lưu khoá file ở đây. */
@Getter
@Setter
@Entity
@Table(name = "job_applications")
public class JobApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    /** Cơ sở ứng viên muốn làm — quyết định MANAGER nào thấy hồ sơ. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 2000)
    private String message;

    @Column(name = "cv_file_key", length = 100)
    private String cvFileKey;

    @Column(name = "cv_original_name", length = 255)
    private String cvOriginalName;

    @Column(name = "cv_content_type", length = 100)
    private String cvContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.NEW;

    @Column(name = "internal_note", length = 2000)
    private String internalNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    public boolean hasCv() {
        return cvFileKey != null;
    }
}
```

- [ ] **Step 8: `M/entity/Feedback.java`**

```java
package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Phản hồi của khách (spec D §2.5). store NULL = phản hồi chung toàn chuỗi, chỉ ADMIN thấy. */
@Getter
@Setter
@Entity
@Table(name = "feedbacks")
public class Feedback extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id")
    private Store store;

    /** Tên field tránh từ khoá ORDER trong JPQL; cột vẫn là order_id. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order relatedOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 5000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.NEW;

    @Column(name = "resolution_note", length = 2000)
    private String resolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;
}
```

- [ ] **Step 9: Bốn repository**

`M/repository/NewsPostRepository.java`:

```java
package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.enums.NewsStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {

    String VISIBLE = "p.deleted = false AND p.status = com.banhmyking.banhmyking.enums.NewsStatus.PUBLISHED "
            + "AND p.publishedAt <= :now";

    Optional<NewsPost> findByIdAndDeletedFalse(Long id);

    Optional<NewsPost> findBySlugAndDeletedFalse(String slug);

    /** Slug đã bị chiếm bởi bất kỳ dòng nào, kể cả bài đã xoá mềm (khớp uk_news_posts_slug). */
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Bài khách thấy; thứ tự truyền qua Pageable. */
    @Query("SELECT p FROM NewsPost p WHERE " + VISIBLE)
    Page<NewsPost> findVisible(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT p FROM NewsPost p WHERE " + VISIBLE + " AND p.id <> :excludeId")
    List<NewsPost> findVisibleExcluding(@Param("now") LocalDateTime now, @Param("excludeId") Long excludeId,
                                        Pageable pageable);

    /** null = bỏ qua điều kiện. publishedAfter → hẹn giờ; publishedUntil → đã đăng. */
    @Query("""
            SELECT p FROM NewsPost p
            WHERE p.deleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (:publishedAfter IS NULL OR p.publishedAt > :publishedAfter)
              AND (:publishedUntil IS NULL OR p.publishedAt <= :publishedUntil)
              AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<NewsPost> searchAdmin(@Param("status") NewsStatus status,
                               @Param("publishedAfter") LocalDateTime publishedAfter,
                               @Param("publishedUntil") LocalDateTime publishedUntil,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
```

`M/repository/JobPostingRepository.java`:

```java
package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.enums.JobStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Optional<JobPosting> findByIdAndDeletedFalse(Long id);

    Optional<JobPosting> findBySlugAndDeletedFalse(String slug);

    /** Slug đã bị chiếm bởi bất kỳ dòng nào, kể cả tin đã xoá mềm (khớp uk_job_postings_slug). */
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /**
     * Tin khách thấy: OPEN, chưa xoá, chưa quá hạn. storeId != null → chỉ tin tuyển ở cơ sở đó
     * hoặc tuyển toàn chuỗi (không gắn cơ sở nào).
     */
    @Query("""
            SELECT j FROM JobPosting j
            WHERE j.deleted = false
              AND j.status = com.banhmyking.banhmyking.enums.JobStatus.OPEN
              AND (j.deadline IS NULL OR j.deadline >= :today)
              AND (:storeId IS NULL OR j.stores IS EMPTY
                   OR EXISTS (SELECT s.id FROM JobPosting j2 JOIN j2.stores s WHERE j2.id = j.id AND s.id = :storeId))
            ORDER BY j.createdAt DESC, j.id DESC
            """)
    List<JobPosting> findOpen(@Param("today") LocalDate today, @Param("storeId") Long storeId);

    @Query("""
            SELECT j FROM JobPosting j
            WHERE j.deleted = false
              AND (:status IS NULL OR j.status = :status)
              AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<JobPosting> searchAdmin(@Param("status") JobStatus status, @Param("keyword") String keyword,
                                 Pageable pageable);
}
```

`M/repository/JobApplicationRepository.java`:

```java
package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /** Chống nộp trùng: cùng SĐT + cùng tin sau mốc `since` (spec D §4). */
    boolean existsByJobPostingIdAndPhoneAndCreatedAtAfter(Long jobPostingId, String phone, LocalDateTime since);

    @Query("""
            SELECT a FROM JobApplication a
            WHERE (:jobId IS NULL OR a.jobPosting.id = :jobId)
              AND (:storeId IS NULL OR a.store.id = :storeId)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<JobApplication> search(@Param("jobId") Long jobId, @Param("storeId") Long storeId,
                                @Param("status") ApplicationStatus status, Pageable pageable);

    /** storeId null = toàn chuỗi (ADMIN). */
    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.status = :status "
            + "AND (:storeId IS NULL OR a.store.id = :storeId)")
    long countByStatusInScope(@Param("status") ApplicationStatus status, @Param("storeId") Long storeId);
}
```

`M/repository/FeedbackRepository.java`:

```java
package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** storeId != null loại luôn phản hồi chung toàn chuỗi (store NULL) — đúng phạm vi MANAGER. */
    @Query("""
            SELECT f FROM Feedback f
            WHERE (:type IS NULL OR f.type = :type)
              AND (:storeId IS NULL OR f.store.id = :storeId)
              AND (:status IS NULL OR f.status = :status)
            """)
    Page<Feedback> search(@Param("type") FeedbackType type, @Param("storeId") Long storeId,
                          @Param("status") FeedbackStatus status, Pageable pageable);

    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.status = :status "
            + "AND (:storeId IS NULL OR f.store.id = :storeId)")
    long countByStatusInScope(@Param("status") FeedbackStatus status, @Param("storeId") Long storeId);
}
```

- [ ] **Step 10: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=ContentSchemaIntegrationTest`
Expected: log Flyway `Successfully applied 1 migration ... now at version v15`, Hibernate validate không lỗi, `Tests run: 4, Failures: 0, Errors: 0` → BUILD SUCCESS.

- [ ] **Step 11: Commit (người dùng tự chạy)**

```
git add src/main/resources/db/migration/V15__news_careers_feedback.sql src/main/java/com/banhmyking/banhmyking/enums src/main/java/com/banhmyking/banhmyking/entity src/main/java/com/banhmyking/banhmyking/repository src/test/java/com/banhmyking/banhmyking/repository/ContentSchemaIntegrationTest.java
git commit -m "feat(content): V15 bảng tin tức, tuyển dụng, hồ sơ ứng tuyển, phản hồi + entity và repository"
```

---

## Task 2: Tiện ích dùng chung — slug, trường liên hệ, bộ đếm IP (429), IP client

**Files:**
- Create: `M/util/SlugUtils.java`, `M/util/ContactFields.java`, `M/security/SubmissionRateLimiter.java`, `M/security/ClientIpResolver.java`
- Modify: `M/exception/ErrorCode.java` (thêm `TOO_MANY_REQUESTS`), `src/main/resources/application.properties` (thêm `app.trust-forwarded-for`)
- Test: `T/util/SlugUtilsTest.java`, `T/util/ContactFieldsTest.java`, `T/security/SubmissionRateLimiterTest.java`, `T/security/ClientIpResolverTest.java`

**Interfaces:**
- Consumes: `BusinessException`, `ErrorCode`, `GlobalExceptionHandler.handleBusiness(BusinessException)`, `TimeConfig.VIETNAM`.
- Produces:
  - `ErrorCode.TOO_MANY_REQUESTS` (HTTP 429).
  - `SlugUtils.slugify(String input): String` (có thể rỗng), `SlugUtils.uniqueSlug(String base, Predicate<String> taken): String`, hằng `SlugUtils.MAX_BASE_LENGTH = 200`.
  - `ContactFields.trimToNull(String)`, `ContactFields.requireText(String value, int max, String requiredMessage, String tooLongMessage): String`, `ContactFields.optionalText(String value, int max, String tooLongMessage): String` (null nếu rỗng), `ContactFields.phone(String raw): String` (null nếu rỗng; bỏ khoảng trắng/chấm/gạch), `ContactFields.email(String raw): String` (null nếu rỗng). Lỗi → `BusinessException(VALIDATION_ERROR, …)`.
  - `SubmissionRateLimiter(Clock)` bean: `void check(String key)` (≥ 5 lần trong 1 giờ → `BusinessException(TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau")`), `void record(String key)`; hằng `MAX_PER_WINDOW = 5`, `WINDOW = Duration.ofHours(1)`, `MESSAGE`.
  - `ClientIpResolver(boolean trustForwardedFor)` bean: `String resolve(HttpServletRequest)` (≤ 45 ký tự, không bao giờ null).

- [ ] **Step 1: Viết 4 test (đỏ)**

`T/util/SlugUtilsTest.java`:

```java
package com.banhmyking.banhmyking.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SlugUtilsTest {

    @Test
    void removesVietnameseDiacriticsIncludingDStroke() {
        assertThat(SlugUtils.slugify("Khuyến mãi tháng 10 — Đặc biệt!")).isEqualTo("khuyen-mai-thang-10-dac-biet");
        assertThat(SlugUtils.slugify("ĐƯỜNG ĐUA Bánh Mì Ơi")).isEqualTo("duong-dua-banh-mi-oi");
        assertThat(SlugUtils.slugify("Phụ bếp ca tối (22–25k/giờ)")).isEqualTo("phu-bep-ca-toi-22-25k-gio");
    }

    @Test
    void collapsesSeparatorsAndTrimsDashes() {
        assertThat(SlugUtils.slugify("  --Hello__World--  ")).isEqualTo("hello-world");
        assertThat(SlugUtils.slugify("a---b")).isEqualTo("a-b");
    }

    @Test
    void emptyWhenNothingUsable() {
        assertThat(SlugUtils.slugify("!!! ???")).isEmpty();
        assertThat(SlugUtils.slugify(null)).isEmpty();
    }

    @Test
    void truncatesLongInputWithoutTrailingDash() {
        String slug = SlugUtils.slugify("ab ".repeat(150));
        assertThat(slug.length()).isLessThanOrEqualTo(SlugUtils.MAX_BASE_LENGTH);
        assertThat(slug).doesNotEndWith("-").startsWith("ab-ab");
    }

    @Test
    void uniqueSlugAddsNumericSuffix() {
        Set<String> taken = Set.of("tin-moi", "tin-moi-2");
        assertThat(SlugUtils.uniqueSlug("tin-moi", taken::contains)).isEqualTo("tin-moi-3");
        assertThat(SlugUtils.uniqueSlug("tin-khac", taken::contains)).isEqualTo("tin-khac");
    }
}
```

`T/util/ContactFieldsTest.java`:

```java
package com.banhmyking.banhmyking.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.exception.BusinessException;
import org.junit.jupiter.api.Test;

class ContactFieldsTest {

    @Test
    void phoneIsNormalisedAndValidatedAsVietnamese() {
        assertThat(ContactFields.phone(" 090 123.45-67 ")).isEqualTo("0901234567");
        assertThat(ContactFields.phone("+84901234567")).isEqualTo("+84901234567");
        assertThat(ContactFields.phone("   ")).isNull();
        assertThat(ContactFields.phone(null)).isNull();
        assertThatThrownBy(() -> ContactFields.phone("0123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Số điện thoại không đúng định dạng Việt Nam");
        assertThatThrownBy(() -> ContactFields.phone("0201234567"))
                .hasMessage("Số điện thoại không đúng định dạng Việt Nam");
    }

    @Test
    void emailIsOptionalButMustLookValid() {
        assertThat(ContactFields.email(" an@banhmy.vn ")).isEqualTo("an@banhmy.vn");
        assertThat(ContactFields.email("")).isNull();
        assertThatThrownBy(() -> ContactFields.email("an@"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Email không đúng định dạng");
        assertThatThrownBy(() -> ContactFields.email("a".repeat(150) + "@x.vn"))
                .hasMessage("Email tối đa 150 ký tự");
    }

    @Test
    void requiredAndOptionalText() {
        assertThat(ContactFields.requireText("  Lan  ", 100, "Vui lòng nhập họ tên", "Họ tên tối đa 100 ký tự"))
                .isEqualTo("Lan");
        assertThatThrownBy(() -> ContactFields.requireText(" ", 100, "Vui lòng nhập họ tên", "x"))
                .hasMessage("Vui lòng nhập họ tên");
        assertThatThrownBy(() -> ContactFields.requireText("a".repeat(101), 100, "x", "Họ tên tối đa 100 ký tự"))
                .hasMessage("Họ tên tối đa 100 ký tự");
        assertThat(ContactFields.optionalText("   ", 2000, "x")).isNull();
        assertThatThrownBy(() -> ContactFields.optionalText("a".repeat(2001), 2000, "Lời nhắn tối đa 2000 ký tự"))
                .hasMessage("Lời nhắn tối đa 2000 ký tự");
        assertThat(ContactFields.trimToNull("  x ")).isEqualTo("x");
    }
}
```

`T/security/SubmissionRateLimiterTest.java`:

```java
package com.banhmyking.banhmyking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.dto.common.ErrorResponse;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SubmissionRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T03:00:00Z"));
    private final SubmissionRateLimiter limiter = new SubmissionRateLimiter(clock);

    @Test
    void sixthSubmissionWithinAnHourIsRejectedWith429() {
        for (int i = 0; i < SubmissionRateLimiter.MAX_PER_WINDOW; i++) {
            limiter.check("10.0.0.1");
            limiter.record("10.0.0.1");
        }
        assertThatThrownBy(() -> limiter.check("10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bạn thao tác quá nhanh, vui lòng thử lại sau")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        assertThatCode(() -> limiter.check("10.0.0.2")).doesNotThrowAnyException();
    }

    @Test
    void windowSlidesSoOldestHitExpiresAfterOneHour() {
        for (int i = 0; i < 5; i++) {
            limiter.record("ip");
            clock.advance(Duration.ofMinutes(10));
        }
        // now = t0 + 50 phút: đủ 5 lần trong cửa sổ
        assertThatThrownBy(() -> limiter.check("ip")).isInstanceOf(BusinessException.class);
        clock.advance(Duration.ofMinutes(10).plusSeconds(1)); // lần đầu (t0) đã quá 1 giờ
        assertThatCode(() -> limiter.check("ip")).doesNotThrowAnyException();
        limiter.record("ip");
        assertThatThrownBy(() -> limiter.check("ip")).isInstanceOf(BusinessException.class);
    }

    @Test
    void checkAloneDoesNotConsumeQuota() {
        for (int i = 0; i < 20; i++) {
            limiter.check("only-check");
        }
        assertThatCode(() -> limiter.check("only-check")).doesNotThrowAnyException();
    }

    @Test
    void globalHandlerMapsTooManyRequestsTo429() {
        ResponseEntity<ErrorResponse> response = new GlobalExceptionHandler().handleBusiness(
                new BusinessException(ErrorCode.TOO_MANY_REQUESTS, SubmissionRateLimiter.MESSAGE));
        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(response.getBody().errorCode()).isEqualTo("TOO_MANY_REQUESTS");
    }

    /** Clock chỉnh tay được để kiểm cửa sổ trượt. */
    static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return TimeConfig.VIETNAM;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
```

`T/security/ClientIpResolverTest.java`:

```java
package com.banhmyking.banhmyking.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void ignoresForwardedHeaderByDefault() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("192.168.1.10");
    }

    @Test
    void usesFirstForwardedAddressWhenTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", " 1.2.3.4 , 10.0.0.1");
        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void neverReturnsNullAndCapsLength() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("unknown");
        request.setRemoteAddr("x".repeat(60));
        assertThat(new ClientIpResolver(false).resolve(request)).hasSize(45);
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest='SlugUtilsTest,ContactFieldsTest,SubmissionRateLimiterTest,ClientIpResolverTest'`
Expected: COMPILATION ERROR (`SlugUtils`, `ContactFields`, `SubmissionRateLimiter`, `ClientIpResolver`, `ErrorCode.TOO_MANY_REQUESTS` chưa tồn tại).

- [ ] **Step 3: `ErrorCode` — thêm 429**

Trong `M/exception/ErrorCode.java`, sửa (Edit) đoạn:

```java
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);
```

thành:

```java
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    /** Chống spam form công khai (hồ sơ ứng tuyển, phản hồi) — 5 lần/giờ/IP. */
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);
```

`GlobalExceptionHandler.handleBusiness` đã trả đúng `ex.getErrorCode().getHttpStatus()` nên không cần sửa handler.

- [ ] **Step 4: `M/util/SlugUtils.java`**

```java
package com.banhmyking.banhmyking.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Slug cho URL tin tức / tuyển dụng: bỏ dấu tiếng Việt (cả đ/Đ), chỉ còn [a-z0-9-] (spec D §3, §8). */
public final class SlugUtils {

    /** Chừa chỗ cho hậu tố "-N" trong cột VARCHAR(220). */
    public static final int MAX_BASE_LENGTH = 200;

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    private SlugUtils() {
    }

    /** Trả chuỗi rỗng nếu đầu vào không có ký tự chữ/số nào dùng được. */
    public static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String text = input.replace('đ', 'd').replace('Đ', 'D');
        text = MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        text = NON_SLUG.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("-");
        text = trimDashes(text);
        if (text.length() > MAX_BASE_LENGTH) {
            text = trimDashes(text.substring(0, MAX_BASE_LENGTH));
        }
        return text;
    }

    /** base, base-2, base-3… — lấy giá trị đầu tiên chưa bị chiếm. */
    public static String uniqueSlug(String base, Predicate<String> taken) {
        if (!taken.test(base)) {
            return base;
        }
        int suffix = 2;
        while (taken.test(base + "-" + suffix)) {
            suffix++;
        }
        return base + "-" + suffix;
    }

    private static String trimDashes(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && text.charAt(start) == '-') {
            start++;
        }
        while (end > start && text.charAt(end - 1) == '-') {
            end--;
        }
        return text.substring(start, end);
    }
}
```

- [ ] **Step 5: `M/util/ContactFields.java`**

```java
package com.banhmyking.banhmyking.util;

import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import java.util.regex.Pattern;

/**
 * Chuẩn hoá + kiểm các trường liên hệ của form công khai (hồ sơ ứng tuyển, phản hồi).
 * SĐT theo đúng quy tắc ô đăng ký ở frontend: 0 hoặc +84, đầu 3/5/7/8/9, thêm 8 số.
 */
public final class ContactFields {

    private static final Pattern VN_PHONE = Pattern.compile("^(0|\\+84)(3|5|7|8|9)[0-9]{8}$");
    private static final Pattern PHONE_SEPARATORS = Pattern.compile("[\\s.-]");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int EMAIL_MAX = 150;

    private ContactFields() {
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String requireText(String value, int max, String requiredMessage, String tooLongMessage) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw invalid(requiredMessage);
        }
        if (trimmed.length() > max) {
            throw invalid(tooLongMessage);
        }
        return trimmed;
    }

    public static String optionalText(String value, int max, String tooLongMessage) {
        String trimmed = trimToNull(value);
        if (trimmed != null && trimmed.length() > max) {
            throw invalid(tooLongMessage);
        }
        return trimmed;
    }

    /** null nếu bỏ trống; bỏ khoảng trắng, dấu chấm, gạch nối trước khi kiểm. */
    public static String phone(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        String normalised = PHONE_SEPARATORS.matcher(trimmed).replaceAll("");
        if (!VN_PHONE.matcher(normalised).matches()) {
            throw invalid("Số điện thoại không đúng định dạng Việt Nam");
        }
        return normalised;
    }

    public static String email(String raw) {
        String trimmed = trimToNull(raw);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > EMAIL_MAX) {
            throw invalid("Email tối đa 150 ký tự");
        }
        if (!EMAIL.matcher(trimmed).matches()) {
            throw invalid("Email không đúng định dạng");
        }
        return trimmed;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
```

- [ ] **Step 6: `M/security/SubmissionRateLimiter.java`**

```java
package com.banhmyking.banhmyking.security;

import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Giới hạn số lần gửi form công khai theo IP: tối đa 5 lần trong 1 giờ (cửa sổ trượt), dùng CHUNG
 * cho hồ sơ ứng tuyển và phản hồi (spec D §4). Lưu trong bộ nhớ — mất khi khởi động lại, không chia sẻ
 * giữa nhiều máy chủ (rủi ro đã chấp nhận ở spec §8). Chỉ lần gửi THÀNH CÔNG mới được {@link #record}.
 */
@Component
public class SubmissionRateLimiter {

    public static final int MAX_PER_WINDOW = 5;
    public static final Duration WINDOW = Duration.ofHours(1);
    public static final String MESSAGE = "Bạn thao tác quá nhanh, vui lòng thử lại sau";

    /** Quá ngưỡng này thì dọn toàn bộ khoá đã hết hạn, tránh map phình mãi. */
    private static final int SWEEP_THRESHOLD = 1000;

    private final Clock clock;
    private final Map<String, Deque<Instant>> hits = new HashMap<>();

    public SubmissionRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Ném 429 nếu khoá đã dùng hết lượt trong cửa sổ hiện tại. Không tiêu lượt. */
    public synchronized void check(String key) {
        Deque<Instant> recent = prune(normalise(key), clock.instant());
        if (recent != null && recent.size() >= MAX_PER_WINDOW) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, MESSAGE);
        }
    }

    /** Ghi nhận một lần gửi thành công. */
    public synchronized void record(String key) {
        Instant now = clock.instant();
        String normalised = normalise(key);
        prune(normalised, now);
        hits.computeIfAbsent(normalised, k -> new ArrayDeque<>()).addLast(now);
        if (hits.size() > SWEEP_THRESHOLD) {
            Instant cutoff = now.minus(WINDOW);
            hits.values().forEach(deque -> {
                while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
                    deque.pollFirst();
                }
            });
            hits.values().removeIf(Deque::isEmpty);
        }
    }

    /** Bỏ các mốc đã ra khỏi cửa sổ; trả null (và xoá khoá) khi không còn mốc nào. */
    private Deque<Instant> prune(String key, Instant now) {
        Deque<Instant> recent = hits.get(key);
        if (recent == null) {
            return null;
        }
        Instant cutoff = now.minus(WINDOW);
        while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
            recent.pollFirst();
        }
        if (recent.isEmpty()) {
            hits.remove(key);
            return null;
        }
        return recent;
    }

    private static String normalise(String key) {
        return key == null || key.isBlank() ? "unknown" : key;
    }
}
```

- [ ] **Step 7: `M/security/ClientIpResolver.java`**

```java
package com.banhmyking.banhmyking.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IP client cho chống spam + lưu client_ip. Mặc định chỉ tin remoteAddr: header X-Forwarded-For
 * do client tự đặt được nên chỉ đọc khi triển khai sau reverse proxy tin cậy (app.trust-forwarded-for=true).
 */
@Component
public class ClientIpResolver {

    private static final int MAX_LENGTH = 45;

    private final boolean trustForwardedFor;

    public ClientIpResolver(@Value("${app.trust-forwarded-for:false}") boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public String resolve(HttpServletRequest request) {
        String ip = null;
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                ip = forwarded.split(",")[0].trim();
            }
        }
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        if (ip == null || ip.isBlank()) {
            return "unknown";
        }
        return ip.length() > MAX_LENGTH ? ip.substring(0, MAX_LENGTH) : ip;
    }
}
```

- [ ] **Step 8: `application.properties` — thêm cuối file**

```properties

# Chống spam form công khai: chỉ đọc X-Forwarded-For khi chạy sau reverse proxy tin cậy
app.trust-forwarded-for=false
```

- [ ] **Step 9: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest='SlugUtilsTest,ContactFieldsTest,SubmissionRateLimiterTest,ClientIpResolverTest'`
Expected: `Tests run: 15, Failures: 0, Errors: 0` → BUILD SUCCESS.

- [ ] **Step 10: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/util/SlugUtils.java src/main/java/com/banhmyking/banhmyking/util/ContactFields.java src/main/java/com/banhmyking/banhmyking/security/SubmissionRateLimiter.java src/main/java/com/banhmyking/banhmyking/security/ClientIpResolver.java src/main/java/com/banhmyking/banhmyking/exception/ErrorCode.java src/main/resources/application.properties src/test/java/com/banhmyking/banhmyking/util src/test/java/com/banhmyking/banhmyking/security
git commit -m "feat(content): slug bỏ dấu, kiểm trường liên hệ, giới hạn 5 lần/giờ/IP (429) và lấy IP client"
```

---

## Task 3: Tin tức — `NewsService` + API công khai và ADMIN

**Files:**
- Create: `M/dto/news/NewsRequest.java`, `M/dto/news/NewsSummaryResponse.java`, `M/dto/news/NewsDetailResponse.java`, `M/dto/news/AdminNewsResponse.java`
- Create: `M/service/NewsService.java`
- Create: `M/controller/NewsController.java`, `M/controller/AdminNewsController.java`
- Test: `T/service/NewsServiceTest.java`, `T/controller/NewsControllerTest.java`

**Interfaces:**
- Consumes: `NewsPost` (`isVisibleAt`, `displayStateAt`), `NewsPostRepository` (Task 1); `SlugUtils.slugify/uniqueSlug`, `ContactFields.trimToNull/optionalText` (Task 2); `UserRepository`, `PageResponse.from(Page)`, `PageableFactory.of(int, int, Sort)`, `SecurityUtils.requireUserId(UserDetails)`.
- Produces:
  - `record NewsRequest(String title, String slug, String coverImageUrl, String summary, String content, NewsStatus status, LocalDateTime publishedAt, Boolean pinned)`.
  - `record NewsSummaryResponse(Long id, String title, String slug, String coverImageUrl, String summary, LocalDateTime publishedAt, boolean pinned)` + `static from(NewsPost)`.
  - `record NewsDetailResponse(Long id, String title, String slug, String coverImageUrl, String summary, String content, LocalDateTime publishedAt, List<NewsSummaryResponse> related)` + `static of(NewsPost, List<NewsPost>)`.
  - `record AdminNewsResponse(Long id, String title, String slug, String coverImageUrl, String summary, String content, NewsStatus status, NewsDisplayState displayState, LocalDateTime publishedAt, boolean pinned, String authorName, LocalDateTime createdAt, LocalDateTime updatedAt)` + `static from(NewsPost, LocalDateTime now, boolean withContent)` (danh sách: `content = null`).
  - `NewsService(NewsPostRepository, UserRepository, Clock)`: `PageResponse<NewsSummaryResponse> listPublished(int page, int size)`, `List<NewsSummaryResponse> latest(int limit)` (kẹp 1–12), `NewsDetailResponse getPublished(String slug)`, `PageResponse<AdminNewsResponse> searchAdmin(NewsDisplayState status, String keyword, int page, int size)`, `AdminNewsResponse getAdmin(Long id)`, `AdminNewsResponse create(Long actorId, NewsRequest)`, `AdminNewsResponse update(Long id, NewsRequest)`, `void delete(Long id)`.
  - HTTP: `GET /api/v1/news?page=0&size=9`, `GET /api/v1/news/latest?limit=3`, `GET /api/v1/news/{slug}`; ADMIN `GET /api/v1/admin/news?status&keyword&page=0&size=20`, `GET /api/v1/admin/news/{id}`, `POST` (201), `PUT /{id}`, `DELETE /{id}`.

- [ ] **Step 1: Viết test service (đỏ)**

`T/service/NewsServiceTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.NewsPostRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    /** 10:00 sáng 02/10/2026 giờ Việt Nam. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Mock private NewsPostRepository newsPostRepository;
    @Mock private UserRepository userRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM);
    private NewsService service;

    @BeforeEach
    void setUp() {
        service = new NewsService(newsPostRepository, userRepository, clock);
    }

    @Test
    void createGeneratesSlugWithoutDiacriticsAndAddsSuffixWhenTaken() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "Quản trị")));
        when(newsPostRepository.existsBySlug("khuyen-mai-thang-10-dac-biet")).thenReturn(true);
        when(newsPostRepository.existsBySlug("khuyen-mai-thang-10-dac-biet-2")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(11L));

        AdminNewsResponse created = service.create(7L,
                request("Khuyến mãi tháng 10 — Đặc biệt", null, NewsStatus.DRAFT, null));

        assertThat(created.id()).isEqualTo(11L);
        assertThat(created.slug()).isEqualTo("khuyen-mai-thang-10-dac-biet-2");
        assertThat(created.displayState()).isEqualTo(NewsDisplayState.DRAFT);
        assertThat(created.authorName()).isEqualTo("Quản trị");
        assertThat(created.content()).isEqualTo("# Nội dung");
    }

    @Test
    void publishWithoutPublishedAtUsesClockNow() {
        when(newsPostRepository.existsBySlug("ra-mat-banh-mi-moi")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(12L));

        AdminNewsResponse created = service.create(7L, request("Ra mắt bánh mì mới", null, NewsStatus.PUBLISHED, null));

        assertThat(created.publishedAt()).isEqualTo(NOW);
        assertThat(created.displayState()).isEqualTo(NewsDisplayState.PUBLISHED);
    }

    @Test
    void futurePublishedAtIsScheduledAndAdminSlugIsNormalised() {
        when(newsPostRepository.existsBySlug("hen-gio-dang")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(13L));

        AdminNewsResponse created = service.create(7L,
                request("Bài hẹn giờ", "  Hẹn Giờ Đăng ", NewsStatus.PUBLISHED, NOW.plusDays(1)));

        assertThat(created.slug()).isEqualTo("hen-gio-dang");
        assertThat(created.displayState()).isEqualTo(NewsDisplayState.SCHEDULED);
        assertThat(created.publishedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void rejectsShortTitleBlankContentAndUnusableSlug() {
        assertThatThrownBy(() -> service.create(7L, request("Ab", null, NewsStatus.DRAFT, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tiêu đề phải từ 3 đến 200 ký tự");
        assertThatThrownBy(() -> service.create(7L,
                new NewsRequest("Tiêu đề hợp lệ", null, null, null, "   ", NewsStatus.DRAFT, null, false)))
                .hasMessage("Nội dung bài viết không được để trống");
        assertThatThrownBy(() -> service.create(7L, request("Tiêu đề hợp lệ", "!!!", NewsStatus.DRAFT, null)))
                .hasMessage("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang");
        verify(newsPostRepository, never()).save(any());
    }

    @Test
    void updateWithBlankSlugKeepsExistingSlug() {
        NewsPost existing = post(5L, "slug-cu", NewsStatus.PUBLISHED, NOW.minusDays(1));
        when(newsPostRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(existing));
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminNewsResponse updated = service.update(5L,
                request("Tiêu đề mới hoàn toàn", "", NewsStatus.PUBLISHED, NOW.minusDays(1)));

        assertThat(updated.slug()).isEqualTo("slug-cu");
        assertThat(updated.title()).isEqualTo("Tiêu đề mới hoàn toàn");
        verify(newsPostRepository, never()).existsBySlugAndIdNot(anyString(), anyLong());
    }

    @Test
    void updateWithNewSlugOnlyChecksOtherRows() {
        when(newsPostRepository.findByIdAndDeletedFalse(5L))
                .thenReturn(Optional.of(post(5L, "slug-cu", NewsStatus.DRAFT, null)));
        when(newsPostRepository.existsBySlugAndIdNot("slug-moi", 5L)).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(5L, request("Tiêu đề", "slug-moi", NewsStatus.DRAFT, null)).slug())
                .isEqualTo("slug-moi");
    }

    @Test
    void publicDetailHidesDraftScheduledAndMissingPosts() {
        when(newsPostRepository.findBySlugAndDeletedFalse("nhap"))
                .thenReturn(Optional.of(post(1L, "nhap", NewsStatus.DRAFT, null)));
        when(newsPostRepository.findBySlugAndDeletedFalse("hen-gio"))
                .thenReturn(Optional.of(post(2L, "hen-gio", NewsStatus.PUBLISHED, NOW.plusMinutes(1))));
        when(newsPostRepository.findBySlugAndDeletedFalse("khong-co")).thenReturn(Optional.empty());

        for (String slug : List.of("nhap", "hen-gio", "khong-co")) {
            assertThatThrownBy(() -> service.getPublished(slug))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy bài viết");
        }
    }

    @Test
    void publicDetailReturnsPostWithUpToThreeRelated() {
        when(newsPostRepository.findBySlugAndDeletedFalse("bai-chinh"))
                .thenReturn(Optional.of(post(3L, "bai-chinh", NewsStatus.PUBLISHED, NOW)));
        when(newsPostRepository.findVisibleExcluding(eq(NOW), eq(3L), any(Pageable.class)))
                .thenReturn(List.of(post(4L, "b4", NewsStatus.PUBLISHED, NOW.minusDays(1)),
                        post(5L, "b5", NewsStatus.PUBLISHED, NOW.minusDays(2))));

        NewsDetailResponse detail = service.getPublished("bai-chinh");

        assertThat(detail.content()).isEqualTo("# Nội dung");
        assertThat(detail.related()).extracting(NewsSummaryResponse::id).containsExactly(4L, 5L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsPostRepository).findVisibleExcluding(eq(NOW), eq(3L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void adminScheduledFilterMeansPublishedAfterNow() {
        when(newsPostRepository.searchAdmin(eq(NewsStatus.PUBLISHED), eq(NOW), isNull(), eq("khuyen"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(9L, "km", NewsStatus.PUBLISHED, NOW.plusDays(2)))));

        PageResponse<AdminNewsResponse> page = service.searchAdmin(NewsDisplayState.SCHEDULED, " khuyen ", 0, 20);

        assertThat(page.content()).singleElement().satisfies(item -> {
            assertThat(item.displayState()).isEqualTo(NewsDisplayState.SCHEDULED);
            assertThat(item.content()).isNull();
        });
    }

    @Test
    void latestClampsLimitToTwelve() {
        when(newsPostRepository.findVisible(eq(NOW), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.latest(100);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsPostRepository).findVisible(eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
    }

    @Test
    void deleteIsSoft() {
        NewsPost existing = post(6L, "xoa", NewsStatus.DRAFT, null);
        when(newsPostRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(existing));

        service.delete(6L);

        assertThat(existing.isDeleted()).isTrue();
        verify(newsPostRepository).save(existing);
    }

    // ----------------------------------------------------------------------- helpers

    private static NewsRequest request(String title, String slug, NewsStatus status, LocalDateTime publishedAt) {
        return new NewsRequest(title, slug, null, "Tóm tắt", "# Nội dung", status, publishedAt, false);
    }

    private static NewsPost post(Long id, String slug, NewsStatus status, LocalDateTime publishedAt) {
        NewsPost post = new NewsPost();
        post.setId(id);
        post.setTitle("Bài " + slug);
        post.setSlug(slug);
        post.setContent("# Nội dung");
        post.setStatus(status);
        post.setPublishedAt(publishedAt);
        return post;
    }

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        return user;
    }

    private static Answer<NewsPost> saveWithId(Long id) {
        return inv -> {
            NewsPost post = inv.getArgument(0);
            post.setId(id);
            return post;
        };
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=NewsServiceTest`
Expected: COMPILATION ERROR (`NewsService`, `NewsRequest`, `AdminNewsResponse`, `NewsDetailResponse`, `NewsSummaryResponse` chưa tồn tại).

- [ ] **Step 3: DTO trong `M/dto/news/`**

`NewsRequest.java`:

```java
package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.enums.NewsStatus;
import java.time.LocalDateTime;

/**
 * Tạo/sửa bài. Kiểm ở NewsService (thông báo tiếng Việt). slug bỏ trống: tạo → sinh từ tiêu đề,
 * sửa → giữ slug cũ. publishedAt là giờ Việt Nam; PUBLISHED mà bỏ trống → server đặt "bây giờ".
 */
public record NewsRequest(
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        String content,
        NewsStatus status,
        LocalDateTime publishedAt,
        Boolean pinned
) {
}
```

`NewsSummaryResponse.java`:

```java
package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.entity.NewsPost;
import java.time.LocalDateTime;

/** Thẻ bài trên lưới tin tức / khối "Tin mới" — không chứa nội dung. */
public record NewsSummaryResponse(
        Long id,
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        LocalDateTime publishedAt,
        boolean pinned
) {
    public static NewsSummaryResponse from(NewsPost post) {
        return new NewsSummaryResponse(post.getId(), post.getTitle(), post.getSlug(), post.getCoverImageUrl(),
                post.getSummary(), post.getPublishedAt(), post.isPinned());
    }
}
```

`NewsDetailResponse.java`:

```java
package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.entity.NewsPost;
import java.time.LocalDateTime;
import java.util.List;

/** Trang bài viết của khách: nội dung Markdown + tối đa 3 bài liên quan mới nhất. */
public record NewsDetailResponse(
        Long id,
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        String content,
        LocalDateTime publishedAt,
        List<NewsSummaryResponse> related
) {
    public static NewsDetailResponse of(NewsPost post, List<NewsPost> related) {
        return new NewsDetailResponse(post.getId(), post.getTitle(), post.getSlug(), post.getCoverImageUrl(),
                post.getSummary(), post.getContent(), post.getPublishedAt(),
                related.stream().map(NewsSummaryResponse::from).toList());
    }
}
```

`AdminNewsResponse.java`:

```java
package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import java.time.LocalDateTime;

/** Bài cho màn quản trị — kèm trạng thái hiển thị suy ra (Nháp / Hẹn giờ / Đã đăng). */
public record AdminNewsResponse(
        Long id,
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        String content,
        NewsStatus status,
        NewsDisplayState displayState,
        LocalDateTime publishedAt,
        boolean pinned,
        String authorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** withContent = false cho danh sách (không kéo MEDIUMTEXT về bảng). */
    public static AdminNewsResponse from(NewsPost post, LocalDateTime now, boolean withContent) {
        return new AdminNewsResponse(post.getId(), post.getTitle(), post.getSlug(), post.getCoverImageUrl(),
                post.getSummary(), withContent ? post.getContent() : null, post.getStatus(),
                post.displayStateAt(now), post.getPublishedAt(), post.isPinned(),
                post.getAuthor() == null ? null : post.getAuthor().getFullName(),
                post.getCreatedAt(), post.getUpdatedAt());
    }
}
```

- [ ] **Step 4: `M/service/NewsService.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.NewsPostRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.util.ContactFields;
import com.banhmyking.banhmyking.util.PageableFactory;
import com.banhmyking.banhmyking.util.SlugUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tin tức (spec D §3): khách chỉ thấy bài PUBLISHED có published_at ≤ now (Clock giờ Việt Nam). */
@Service
@RequiredArgsConstructor
public class NewsService {

    public static final int RELATED_COUNT = 3;
    public static final int MAX_LATEST = 12;
    static final String NOT_FOUND = "Không tìm thấy bài viết";
    private static final String DEFAULT_SLUG = "tin-tuc";

    private static final Sort PUBLIC_ORDER = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"),
            Sort.Order.desc("id"));
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private static final Sort ADMIN_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NewsPostRepository newsPostRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // ------------------------------------------------------------------ khách

    @Transactional(readOnly = true)
    public PageResponse<NewsSummaryResponse> listPublished(int page, int size) {
        return PageResponse.from(newsPostRepository.findVisible(now(), PageableFactory.of(page, size, PUBLIC_ORDER))
                .map(NewsSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public List<NewsSummaryResponse> latest(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), MAX_LATEST);
        return newsPostRepository.findVisible(now(), PageRequest.of(0, safeLimit, NEWEST_FIRST)).getContent()
                .stream().map(NewsSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse getPublished(String slug) {
        LocalDateTime now = now();
        NewsPost post = newsPostRepository.findBySlugAndDeletedFalse(slug)
                .filter(p -> p.isVisibleAt(now))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        List<NewsPost> related = newsPostRepository.findVisibleExcluding(now, post.getId(),
                PageRequest.of(0, RELATED_COUNT, NEWEST_FIRST));
        return NewsDetailResponse.of(post, related);
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional(readOnly = true)
    public PageResponse<AdminNewsResponse> searchAdmin(NewsDisplayState state, String keyword, int page, int size) {
        LocalDateTime now = now();
        NewsStatus status = null;
        LocalDateTime publishedAfter = null;
        LocalDateTime publishedUntil = null;
        if (state == NewsDisplayState.DRAFT) {
            status = NewsStatus.DRAFT;
        } else if (state == NewsDisplayState.SCHEDULED) {
            status = NewsStatus.PUBLISHED;
            publishedAfter = now;
        } else if (state == NewsDisplayState.PUBLISHED) {
            status = NewsStatus.PUBLISHED;
            publishedUntil = now;
        }
        return PageResponse.from(newsPostRepository.searchAdmin(status, publishedAfter, publishedUntil,
                        ContactFields.trimToNull(keyword), PageableFactory.of(page, size, ADMIN_ORDER))
                .map(post -> AdminNewsResponse.from(post, now, false)));
    }

    @Transactional(readOnly = true)
    public AdminNewsResponse getAdmin(Long id) {
        return AdminNewsResponse.from(requirePost(id), now(), true);
    }

    @Transactional
    public AdminNewsResponse create(Long actorId, NewsRequest request) {
        NewsPost post = new NewsPost();
        post.setAuthor(userRepository.findById(actorId).orElse(null));
        apply(post, request);
        return AdminNewsResponse.from(newsPostRepository.save(post), now(), true);
    }

    @Transactional
    public AdminNewsResponse update(Long id, NewsRequest request) {
        NewsPost post = requirePost(id);
        apply(post, request);
        return AdminNewsResponse.from(newsPostRepository.save(post), now(), true);
    }

    @Transactional
    public void delete(Long id) {
        NewsPost post = requirePost(id);
        post.setDeleted(true);
        newsPostRepository.save(post);
    }

    // ------------------------------------------------------------------ nội bộ

    private void apply(NewsPost post, NewsRequest request) {
        String title = ContactFields.trimToNull(request.title());
        if (title == null || title.length() < 3 || title.length() > 200) {
            throw invalid("Tiêu đề phải từ 3 đến 200 ký tự");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw invalid("Nội dung bài viết không được để trống");
        }
        String summary = ContactFields.optionalText(request.summary(), 500, "Tóm tắt tối đa 500 ký tự");
        String cover = ContactFields.optionalText(request.coverImageUrl(), 500, "Đường dẫn ảnh bìa tối đa 500 ký tự");
        String slug = resolveSlug(post, request.slug(), title);
        NewsStatus status = request.status() == null ? NewsStatus.DRAFT : request.status();

        post.setTitle(title);
        post.setContent(request.content());
        post.setSummary(summary);
        post.setCoverImageUrl(cover);
        post.setSlug(slug);
        post.setStatus(status);
        post.setPublishedAt(status == NewsStatus.PUBLISHED && request.publishedAt() == null
                ? now() : request.publishedAt());
        post.setPinned(Boolean.TRUE.equals(request.pinned()));
    }

    private String resolveSlug(NewsPost post, String requested, String title) {
        Long selfId = post.getId();
        String wanted;
        if (ContactFields.trimToNull(requested) != null) {
            wanted = SlugUtils.slugify(requested);
            if (wanted.isEmpty()) {
                throw invalid("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang");
            }
        } else if (selfId != null) {
            return post.getSlug();
        } else {
            wanted = SlugUtils.slugify(title);
            if (wanted.isEmpty()) {
                wanted = DEFAULT_SLUG;
            }
        }
        if (wanted.equals(post.getSlug())) {
            return wanted;
        }
        return SlugUtils.uniqueSlug(wanted, candidate -> selfId == null
                ? newsPostRepository.existsBySlug(candidate)
                : newsPostRepository.existsBySlugAndIdNot(candidate, selfId));
    }

    private NewsPost requirePost(Long id) {
        return newsPostRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
```

- [ ] **Step 5: Chạy test service, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=NewsServiceTest`
Expected: `Tests run: 11, Failures: 0, Errors: 0`.

- [ ] **Step 6: Viết test controller (đỏ)**

`T/controller/NewsControllerTest.java`:

```java
package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.service.NewsService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Standalone MockMvc — phân quyền URL kiểm ở NewsCareersSecurityTest (Task 9). */
@ExtendWith(MockitoExtension.class)
class NewsControllerTest {

    private static final UserDetails ADMIN = User.withUsername("7").password("x").authorities("ROLE_ADMIN").build();

    @Mock private NewsService newsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NewsController(newsService), new AdminNewsController(newsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ADMIN, null, ADMIN.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicListUsesDefaultPaging() throws Exception {
        when(newsService.listPublished(0, 9)).thenReturn(new PageResponse<>(List.of(summary()), 0, 9, 1, 1, true));

        mockMvc.perform(get("/api/v1/news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].slug").value("ra-mat"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void latestAndDetail() throws Exception {
        when(newsService.latest(3)).thenReturn(List.of(summary()));
        when(newsService.getPublished("ra-mat")).thenReturn(new NewsDetailResponse(1L, "Ra mắt", "ra-mat", null,
                null, "# Nội dung", LocalDateTime.of(2026, 10, 1, 8, 0), List.of()));

        mockMvc.perform(get("/api/v1/news/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Ra mắt"));
        mockMvc.perform(get("/api/v1/news/ra-mat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("# Nội dung"));
    }

    @Test
    void hiddenPostIs404() throws Exception {
        when(newsService.getPublished("nhap")).thenThrow(new ResourceNotFoundException("Không tìm thấy bài viết"));

        mockMvc.perform(get("/api/v1/news/nhap"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Không tìm thấy bài viết"));
    }

    @Test
    void adminCreatePassesActorAndParsesVietnamLocalTime() throws Exception {
        when(newsService.create(eq(7L), any(NewsRequest.class))).thenReturn(adminResponse());

        mockMvc.perform(post("/api/v1/admin/news")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ra mắt","content":"# Nội dung","status":"PUBLISHED",
                                 "publishedAt":"2026-10-05T08:30","pinned":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));

        ArgumentCaptor<NewsRequest> captor = ArgumentCaptor.forClass(NewsRequest.class);
        verify(newsService).create(eq(7L), captor.capture());
        assertThat(captor.getValue().publishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 5, 8, 30));
        assertThat(captor.getValue().status()).isEqualTo(NewsStatus.PUBLISHED);
        assertThat(captor.getValue().pinned()).isTrue();
    }

    @Test
    void adminListGetUpdateDelete() throws Exception {
        when(newsService.searchAdmin(NewsDisplayState.SCHEDULED, "km", 0, 20))
                .thenReturn(new PageResponse<>(List.of(adminResponse()), 0, 20, 1, 1, true));
        when(newsService.getAdmin(1L)).thenReturn(adminResponse());
        when(newsService.update(eq(1L), any(NewsRequest.class))).thenReturn(adminResponse());

        mockMvc.perform(get("/api/v1/admin/news").param("status", "SCHEDULED").param("keyword", "km"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].displayState").value("PUBLISHED"));
        mockMvc.perform(get("/api/v1/admin/news/1")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/admin/news/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ra mắt\",\"content\":\"x\",\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/news/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xoá bài viết"));
        verify(newsService).delete(1L);
    }

    private static NewsSummaryResponse summary() {
        return new NewsSummaryResponse(1L, "Ra mắt", "ra-mat", null, "Tóm tắt",
                LocalDateTime.of(2026, 10, 1, 8, 0), false);
    }

    private static AdminNewsResponse adminResponse() {
        return new AdminNewsResponse(1L, "Ra mắt", "ra-mat", null, null, "# Nội dung", NewsStatus.PUBLISHED,
                NewsDisplayState.PUBLISHED, LocalDateTime.of(2026, 10, 1, 8, 0), false, "Quản trị",
                LocalDateTime.of(2026, 10, 1, 7, 0), null);
    }
}
```

- [ ] **Step 7: Chạy test controller, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=NewsControllerTest`
Expected: COMPILATION ERROR (`NewsController`, `AdminNewsController` chưa tồn tại).

- [ ] **Step 8: `M/controller/NewsController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Tag(name = "News", description = "Tin tức công khai (spec D §3)")
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    @Operation(summary = "Bài đã đăng", description = "Ghim trước rồi mới nhất trước; không trả nội dung.")
    public ResponseEntity<ApiResponse<PageResponse<NewsSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "9") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tin tức thành công",
                newsService.listPublished(page, size)));
    }

    @GetMapping("/latest")
    @Operation(summary = "Tin mới nhất cho trang chủ")
    public ResponseEntity<ApiResponse<List<NewsSummaryResponse>>> latest(@RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin mới thành công", newsService.latest(limit)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Chi tiết bài + 3 bài liên quan", description = "Bài nháp/hẹn giờ/đã xoá → 404.")
    public ResponseEntity<ApiResponse<NewsDetailResponse>> detail(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy bài viết thành công", newsService.getPublished(slug)));
    }
}
```

- [ ] **Step 9: `M/controller/AdminNewsController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/news")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin news", description = "Soạn tin tức (chỉ ADMIN)")
public class AdminNewsController {

    private final NewsService newsService;

    @GetMapping
    @Operation(summary = "Danh sách bài", description = "status = DRAFT | SCHEDULED | PUBLISHED (suy ra từ published_at).")
    public ResponseEntity<ApiResponse<PageResponse<AdminNewsResponse>>> list(
            @RequestParam(required = false) NewsDisplayState status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách bài viết thành công",
                newsService.searchAdmin(status, keyword, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminNewsResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy bài viết thành công", newsService.getAdmin(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminNewsResponse>> create(@RequestBody NewsRequest request,
                                                                 @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã tạo bài viết",
                newsService.create(SecurityUtils.requireUserId(principal), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminNewsResponse>> update(@PathVariable Long id,
                                                                 @RequestBody NewsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật bài viết", newsService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        newsService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xoá bài viết"));
    }
}
```

- [ ] **Step 10: Chạy cả hai test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest='NewsServiceTest,NewsControllerTest'`
Expected: `Tests run: 16, Failures: 0, Errors: 0` → BUILD SUCCESS.

- [ ] **Step 11: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/news src/main/java/com/banhmyking/banhmyking/service/NewsService.java src/main/java/com/banhmyking/banhmyking/controller/NewsController.java src/main/java/com/banhmyking/banhmyking/controller/AdminNewsController.java src/test/java/com/banhmyking/banhmyking/service/NewsServiceTest.java src/test/java/com/banhmyking/banhmyking/controller/NewsControllerTest.java
git commit -m "feat(news): API tin tức công khai (ghim, hẹn giờ, bài liên quan) và soạn bài cho ADMIN"
```

---

## Task 4: Tin tuyển dụng — `JobPostingService` + API công khai và ADMIN

**Files:**
- Create: `M/dto/job/StoreRef.java`, `M/dto/job/JobPostingRequest.java`, `M/dto/job/JobStatusRequest.java`, `M/dto/job/JobSummaryResponse.java`, `M/dto/job/JobDetailResponse.java`, `M/dto/job/AdminJobResponse.java`
- Create: `M/service/JobPostingService.java`
- Create: `M/controller/JobController.java`, `M/controller/AdminJobController.java`
- Test: `T/service/JobPostingServiceTest.java`, `T/controller/JobControllerTest.java`

**Interfaces:**
- Consumes: `JobPosting` (`isChainWide`, `acceptsApplicationsOn`, `isExpiredOn`), `JobPostingRepository` (Task 1); `SlugUtils`, `ContactFields` (Task 2); `StoreRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()`, `StoreRepository.findByIdAndDeletedFalse(Long)`; `PageResponse`, `PageableFactory`.
- Produces:
  - `record StoreRef(Long id, String code, String name)` + `static from(Store)`.
  - `record JobPostingRequest(String title, String slug, EmploymentType employmentType, String salaryText, Integer headcount, LocalDate deadline, String description, JobStatus status, List<Long> storeIds)` — `storeIds` rỗng/null = toàn chuỗi.
  - `record JobStatusRequest(JobStatus status)`.
  - `record JobSummaryResponse(Long id, String title, String slug, EmploymentType employmentType, String salaryText, Integer headcount, LocalDate deadline, boolean chainWide, List<StoreRef> stores, boolean acceptingApplications)` + `static of(JobPosting, List<Store> receivingStores, LocalDate today)`.
  - `record JobDetailResponse(Long id, String title, String slug, EmploymentType employmentType, String salaryText, Integer headcount, LocalDate deadline, String description, boolean chainWide, List<StoreRef> stores, boolean acceptingApplications)` + `static of(JobPosting, List<Store>, LocalDate)`.
  - `record AdminJobResponse(Long id, String title, String slug, EmploymentType employmentType, String salaryText, Integer headcount, LocalDate deadline, String description, JobStatus status, boolean expired, boolean chainWide, List<StoreRef> stores, LocalDateTime createdAt)` + `static from(JobPosting, LocalDate today)`.
  - `JobPostingService(JobPostingRepository, StoreRepository, Clock)`: `LocalDate today()`, `List<JobSummaryResponse> listOpen(Long storeId)`, `JobDetailResponse getBySlug(String slug)`, `JobPosting requireBySlug(String slug)` (404 "Không tìm thấy tin tuyển dụng"), `List<Store> receivingStores(JobPosting)` (cơ sở đang hoạt động của tin, hoặc mọi cơ sở đang hoạt động nếu toàn chuỗi), `PageResponse<AdminJobResponse> searchAdmin(JobStatus, String keyword, int page, int size)`, `AdminJobResponse getAdmin(Long)`, `AdminJobResponse create(JobPostingRequest)`, `AdminJobResponse update(Long, JobPostingRequest)`, `AdminJobResponse setStatus(Long, JobStatus)`, `void delete(Long)`.
  - HTTP: `GET /api/v1/jobs?storeId`, `GET /api/v1/jobs/{slug}`; ADMIN `GET /api/v1/admin/jobs?status&keyword&page=0&size=20`, `GET /{id}`, `POST` (201), `PUT /{id}`, `PATCH /{id}/status`, `DELETE /{id}`.

- [ ] **Step 1: Viết test service (đỏ)**

`T/service/JobPostingServiceTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.dto.job.StoreRef;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.JobPostingRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private StoreRepository storeRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM);
    private JobPostingService service;

    @BeforeEach
    void setUp() {
        service = new JobPostingService(jobPostingRepository, storeRepository, clock);
    }

    @Test
    void createWithStoresAndGeneratedSlug() {
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(store(1L, "CS-A", true)));
        when(storeRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(store(2L, "CS-B", true)));
        when(jobPostingRepository.existsBySlug("phu-bep-ca-toi")).thenReturn(false);
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> withId(inv.getArgument(0), 21L));

        AdminJobResponse created = service.create(request("Phụ bếp ca tối", TODAY.plusDays(10), List.of(2L, 1L, 2L)));

        assertThat(created.slug()).isEqualTo("phu-bep-ca-toi");
        assertThat(created.status()).isEqualTo(JobStatus.OPEN);
        assertThat(created.chainWide()).isFalse();
        assertThat(created.stores()).extracting(StoreRef::code).containsExactly("CS-A", "CS-B");
    }

    @Test
    void noStoresMeansChainWide() {
        when(jobPostingRepository.existsBySlug("thu-ngan")).thenReturn(false);
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> withId(inv.getArgument(0), 22L));

        AdminJobResponse created = service.create(request("Thu ngân", null, List.of()));

        assertThat(created.chainWide()).isTrue();
        assertThat(created.stores()).isEmpty();
    }

    @Test
    void rejectsInactiveStoreAndInvalidFields() {
        when(storeRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(store(2L, "CS-B", false)));

        assertThatThrownBy(() -> service.create(request("Phụ bếp", null, List.of(2L))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cơ sở không hợp lệ hoặc đã ngừng hoạt động: 2");
        assertThatThrownBy(() -> service.create(request("AB", null, null)))
                .hasMessage("Tên vị trí phải từ 3 đến 200 ký tự");
        assertThatThrownBy(() -> service.create(new JobPostingRequest("Phụ bếp", null, null, null, null, null,
                "Mô tả", null, null)))
                .hasMessage("Vui lòng chọn hình thức làm việc");
        assertThatThrownBy(() -> service.create(new JobPostingRequest("Phụ bếp", null, EmploymentType.FULL_TIME,
                null, 0, null, "Mô tả", null, null)))
                .hasMessage("Số lượng cần tuyển phải từ 1 đến 1000");
        assertThatThrownBy(() -> service.create(new JobPostingRequest("Phụ bếp", null, EmploymentType.FULL_TIME,
                null, null, null, "  ", null, null)))
                .hasMessage("Mô tả công việc không được để trống");
        verify(jobPostingRepository, never()).save(any());
    }

    @Test
    void pastDeadlineRejectedOnCreateButUnchangedPastDeadlineAllowedOnUpdate() {
        assertThatThrownBy(() -> service.create(request("Phụ bếp", TODAY.minusDays(1), null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Hạn nộp hồ sơ không được ở quá khứ");

        JobPosting existing = job(30L, "phu-bep", JobStatus.OPEN, TODAY.minusDays(1));
        when(jobPostingRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminJobResponse updated = service.update(30L, request("Phụ bếp (sửa)", TODAY.minusDays(1), null));

        assertThat(updated.expired()).isTrue();
        assertThat(updated.slug()).isEqualTo("phu-bep");
    }

    @Test
    void acceptingApplicationsFollowsStatusAndDeadline() {
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc())
                .thenReturn(List.of(store(1L, "CS-A", true), store(2L, "CS-B", true)));
        when(jobPostingRepository.findBySlugAndDeletedFalse("dong"))
                .thenReturn(Optional.of(job(1L, "dong", JobStatus.CLOSED, null)));
        when(jobPostingRepository.findBySlugAndDeletedFalse("het-han"))
                .thenReturn(Optional.of(job(2L, "het-han", JobStatus.OPEN, TODAY.minusDays(1))));
        when(jobPostingRepository.findBySlugAndDeletedFalse("hom-nay"))
                .thenReturn(Optional.of(job(3L, "hom-nay", JobStatus.OPEN, TODAY)));

        assertThat(service.getBySlug("dong").acceptingApplications()).isFalse();
        assertThat(service.getBySlug("het-han").acceptingApplications()).isFalse();
        JobDetailResponse today = service.getBySlug("hom-nay");
        assertThat(today.acceptingApplications()).isTrue();
        assertThat(today.chainWide()).isTrue();
        assertThat(today.stores()).extracting(StoreRef::code).containsExactly("CS-A", "CS-B");
    }

    @Test
    void receivingStoresSkipInactiveOrDeletedStoresOfThePosting() {
        Store active = store(1L, "CS-A", true);
        Store inactive = store(2L, "CS-B", false);
        Store deleted = store(3L, "CS-C", true);
        deleted.setDeleted(true);
        JobPosting posting = job(5L, "x", JobStatus.OPEN, null);
        posting.getStores().addAll(List.of(active, inactive, deleted));

        assertThat(service.receivingStores(posting)).containsExactly(active);
    }

    @Test
    void listOpenPassesTodayAndStore() {
        when(jobPostingRepository.findOpen(TODAY, 3L)).thenReturn(List.of(job(9L, "bep", JobStatus.OPEN, null)));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(store(3L, "CS-C", true)));

        List<JobSummaryResponse> jobs = service.listOpen(3L);

        assertThat(jobs).singleElement().satisfies(j -> {
            assertThat(j.slug()).isEqualTo("bep");
            assertThat(j.acceptingApplications()).isTrue();
        });
    }

    @Test
    void setStatusAndSoftDeleteAndMissingSlug() {
        JobPosting existing = job(40L, "bep", JobStatus.OPEN, null);
        when(jobPostingRepository.findByIdAndDeletedFalse(40L)).thenReturn(Optional.of(existing));
        when(jobPostingRepository.save(any(JobPosting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobPostingRepository.findBySlugAndDeletedFalse("khong-co")).thenReturn(Optional.empty());

        assertThat(service.setStatus(40L, JobStatus.CLOSED).status()).isEqualTo(JobStatus.CLOSED);
        service.delete(40L);
        assertThat(existing.isDeleted()).isTrue();
        assertThatThrownBy(() -> service.getBySlug("khong-co"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy tin tuyển dụng");
    }

    // ----------------------------------------------------------------------- helpers

    private static JobPostingRequest request(String title, LocalDate deadline, List<Long> storeIds) {
        return new JobPostingRequest(title, null, EmploymentType.PART_TIME, "22–25k/giờ", 2, deadline,
                "## Mô tả\n- Phụ bếp", null, storeIds);
    }

    private static Store store(Long id, String code, boolean active) {
        Store store = new Store();
        store.setId(id);
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ");
        store.setActive(active);
        return store;
    }

    private static JobPosting job(Long id, String slug, JobStatus status, LocalDate deadline) {
        JobPosting job = new JobPosting();
        job.setId(id);
        job.setTitle("Tin " + slug);
        job.setSlug(slug);
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setDescription("Mô tả");
        job.setStatus(status);
        job.setDeadline(deadline);
        return job;
    }

    private static JobPosting withId(JobPosting job, Long id) {
        job.setId(id);
        return job;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=JobPostingServiceTest`
Expected: COMPILATION ERROR (`JobPostingService` và các DTO `dto.job.*` chưa tồn tại).

- [ ] **Step 3: DTO trong `M/dto/job/`**

`StoreRef.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.Store;

/** Cơ sở rút gọn hiển thị trên tin tuyển dụng / hồ sơ. */
public record StoreRef(Long id, String code, String name) {
    public static StoreRef from(Store store) {
        return new StoreRef(store.getId(), store.getCode(), store.getName());
    }
}
```

`JobPostingRequest.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import java.time.LocalDate;
import java.util.List;

/** Tạo/sửa tin tuyển dụng (ADMIN). storeIds rỗng/null = tuyển toàn chuỗi; status null = OPEN. */
public record JobPostingRequest(
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        String description,
        JobStatus status,
        List<Long> storeIds
) {
}
```

`JobStatusRequest.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.enums.JobStatus;

/** Đóng/mở nhanh tin tuyển dụng. */
public record JobStatusRequest(JobStatus status) {
}
```

`JobSummaryResponse.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import java.time.LocalDate;
import java.util.List;

/** Thẻ tin trên trang /tuyen-dung. stores = cơ sở đang nhận hồ sơ. */
public record JobSummaryResponse(
        Long id,
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        boolean chainWide,
        List<StoreRef> stores,
        boolean acceptingApplications
) {
    public static JobSummaryResponse of(JobPosting job, List<Store> receivingStores, LocalDate today) {
        return new JobSummaryResponse(job.getId(), job.getTitle(), job.getSlug(), job.getEmploymentType(),
                job.getSalaryText(), job.getHeadcount(), job.getDeadline(), job.isChainWide(),
                receivingStores.stream().map(StoreRef::from).toList(), job.acceptsApplicationsOn(today));
    }
}
```

`JobDetailResponse.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import java.time.LocalDate;
import java.util.List;

/** Trang /tuyen-dung/:slug — tin đóng/hết hạn vẫn xem được với acceptingApplications = false. */
public record JobDetailResponse(
        Long id,
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        String description,
        boolean chainWide,
        List<StoreRef> stores,
        boolean acceptingApplications
) {
    public static JobDetailResponse of(JobPosting job, List<Store> receivingStores, LocalDate today) {
        return new JobDetailResponse(job.getId(), job.getTitle(), job.getSlug(), job.getEmploymentType(),
                job.getSalaryText(), job.getHeadcount(), job.getDeadline(), job.getDescription(),
                job.isChainWide(), receivingStores.stream().map(StoreRef::from).toList(),
                job.acceptsApplicationsOn(today));
    }
}
```

`AdminJobResponse.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Tin cho màn quản trị. stores = cơ sở đã cấu hình (rỗng = toàn chuỗi). */
public record AdminJobResponse(
        Long id,
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        String description,
        JobStatus status,
        boolean expired,
        boolean chainWide,
        List<StoreRef> stores,
        LocalDateTime createdAt
) {
    public static AdminJobResponse from(JobPosting job, LocalDate today) {
        return new AdminJobResponse(job.getId(), job.getTitle(), job.getSlug(), job.getEmploymentType(),
                job.getSalaryText(), job.getHeadcount(), job.getDeadline(), job.getDescription(), job.getStatus(),
                job.isExpiredOn(today), job.isChainWide(),
                job.getStores().stream().sorted(Comparator.comparing(Store::getCode)).map(StoreRef::from).toList(),
                job.getCreatedAt());
    }
}
```

- [ ] **Step 4: `M/service/JobPostingService.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.JobPostingRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.util.ContactFields;
import com.banhmyking.banhmyking.util.PageableFactory;
import com.banhmyking.banhmyking.util.SlugUtils;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tin tuyển dụng (spec D §4). Chỉ ADMIN soạn; khách xem tin OPEN chưa hết hạn. */
@Service
@RequiredArgsConstructor
public class JobPostingService {

    static final String NOT_FOUND = "Không tìm thấy tin tuyển dụng";
    private static final String DEFAULT_SLUG = "tuyen-dung";
    private static final int MAX_HEADCOUNT = 1000;
    private static final Sort ADMIN_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final JobPostingRepository jobPostingRepository;
    private final StoreRepository storeRepository;
    private final Clock clock;

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    // ------------------------------------------------------------------ khách

    @Transactional(readOnly = true)
    public List<JobSummaryResponse> listOpen(Long storeId) {
        LocalDate today = today();
        return jobPostingRepository.findOpen(today, storeId).stream()
                .map(job -> JobSummaryResponse.of(job, receivingStores(job), today))
                .toList();
    }

    @Transactional(readOnly = true)
    public JobDetailResponse getBySlug(String slug) {
        JobPosting job = requireBySlug(slug);
        return JobDetailResponse.of(job, receivingStores(job), today());
    }

    public JobPosting requireBySlug(String slug) {
        return jobPostingRepository.findBySlugAndDeletedFalse(slug)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** Cơ sở nhận hồ sơ: cơ sở đang hoạt động của tin, hoặc mọi cơ sở đang hoạt động nếu tuyển toàn chuỗi. */
    public List<Store> receivingStores(JobPosting job) {
        if (job.isChainWide()) {
            return storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc();
        }
        return job.getStores().stream()
                .filter(store -> store.isActive() && !store.isDeleted())
                .sorted(Comparator.comparing(Store::getCode))
                .toList();
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional(readOnly = true)
    public PageResponse<AdminJobResponse> searchAdmin(JobStatus status, String keyword, int page, int size) {
        LocalDate today = today();
        return PageResponse.from(jobPostingRepository.searchAdmin(status, ContactFields.trimToNull(keyword),
                        PageableFactory.of(page, size, ADMIN_ORDER))
                .map(job -> AdminJobResponse.from(job, today)));
    }

    @Transactional(readOnly = true)
    public AdminJobResponse getAdmin(Long id) {
        return AdminJobResponse.from(requireById(id), today());
    }

    @Transactional
    public AdminJobResponse create(JobPostingRequest request) {
        JobPosting job = new JobPosting();
        apply(job, request);
        return AdminJobResponse.from(jobPostingRepository.save(job), today());
    }

    @Transactional
    public AdminJobResponse update(Long id, JobPostingRequest request) {
        JobPosting job = requireById(id);
        apply(job, request);
        return AdminJobResponse.from(jobPostingRepository.save(job), today());
    }

    @Transactional
    public AdminJobResponse setStatus(Long id, JobStatus status) {
        if (status == null) {
            throw invalid("Vui lòng chọn trạng thái tin tuyển dụng");
        }
        JobPosting job = requireById(id);
        job.setStatus(status);
        return AdminJobResponse.from(jobPostingRepository.save(job), today());
    }

    /** Xoá mềm — hồ sơ và CV đã nộp giữ nguyên (spec D §4). */
    @Transactional
    public void delete(Long id) {
        JobPosting job = requireById(id);
        job.setDeleted(true);
        jobPostingRepository.save(job);
    }

    // ------------------------------------------------------------------ nội bộ

    private void apply(JobPosting job, JobPostingRequest request) {
        String title = ContactFields.trimToNull(request.title());
        if (title == null || title.length() < 3 || title.length() > 200) {
            throw invalid("Tên vị trí phải từ 3 đến 200 ký tự");
        }
        if (request.employmentType() == null) {
            throw invalid("Vui lòng chọn hình thức làm việc");
        }
        String salary = ContactFields.optionalText(request.salaryText(), 100, "Mức lương tối đa 100 ký tự");
        Integer headcount = request.headcount();
        if (headcount != null && (headcount < 1 || headcount > MAX_HEADCOUNT)) {
            throw invalid("Số lượng cần tuyển phải từ 1 đến 1000");
        }
        LocalDate deadline = request.deadline();
        if (deadline != null && !deadline.equals(job.getDeadline()) && deadline.isBefore(today())) {
            throw invalid("Hạn nộp hồ sơ không được ở quá khứ");
        }
        if (request.description() == null || request.description().isBlank()) {
            throw invalid("Mô tả công việc không được để trống");
        }
        Set<Store> stores = resolveStores(request.storeIds());
        String slug = resolveSlug(job, request.slug(), title);

        job.setTitle(title);
        job.setSlug(slug);
        job.setEmploymentType(request.employmentType());
        job.setSalaryText(salary);
        job.setHeadcount(headcount);
        job.setDeadline(deadline);
        job.setDescription(request.description());
        job.setStatus(request.status() == null ? JobStatus.OPEN : request.status());
        job.getStores().clear();
        job.getStores().addAll(stores);
    }

    private Set<Store> resolveStores(List<Long> storeIds) {
        Set<Store> stores = new LinkedHashSet<>();
        if (storeIds == null) {
            return stores;
        }
        for (Long storeId : new LinkedHashSet<>(storeIds)) {
            stores.add(storeRepository.findByIdAndDeletedFalse(storeId)
                    .filter(Store::isActive)
                    .orElseThrow(() -> invalid("Cơ sở không hợp lệ hoặc đã ngừng hoạt động: " + storeId)));
        }
        return stores;
    }

    private String resolveSlug(JobPosting job, String requested, String title) {
        Long selfId = job.getId();
        String wanted;
        if (ContactFields.trimToNull(requested) != null) {
            wanted = SlugUtils.slugify(requested);
            if (wanted.isEmpty()) {
                throw invalid("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang");
            }
        } else if (selfId != null) {
            return job.getSlug();
        } else {
            wanted = SlugUtils.slugify(title);
            if (wanted.isEmpty()) {
                wanted = DEFAULT_SLUG;
            }
        }
        if (wanted.equals(job.getSlug())) {
            return wanted;
        }
        return SlugUtils.uniqueSlug(wanted, candidate -> selfId == null
                ? jobPostingRepository.existsBySlug(candidate)
                : jobPostingRepository.existsBySlugAndIdNot(candidate, selfId));
    }

    private JobPosting requireById(Long id) {
        return jobPostingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
```

- [ ] **Step 5: Chạy test service, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=JobPostingServiceTest`
Expected: `Tests run: 8, Failures: 0, Errors: 0`.

- [ ] **Step 6: Viết test controller (đỏ)**

`T/controller/JobControllerTest.java`:

```java
package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.dto.job.StoreRef;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.service.JobPostingService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class JobControllerTest {

    @Mock private JobPostingService jobPostingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new JobController(jobPostingService), new AdminJobController(jobPostingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void publicListFiltersByOptionalStore() throws Exception {
        JobSummaryResponse job = new JobSummaryResponse(1L, "Phụ bếp", "phu-bep", EmploymentType.PART_TIME,
                "22–25k/giờ", 2, null, true, List.of(new StoreRef(3L, "CS03", "Cơ sở 3")), true);
        when(jobPostingService.listOpen(3L)).thenReturn(List.of(job));
        when(jobPostingService.listOpen(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/jobs").param("storeId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].slug").value("phu-bep"))
                .andExpect(jsonPath("$.data[0].stores[0].code").value("CS03"));
        mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void publicDetailAnd404() throws Exception {
        when(jobPostingService.getBySlug("phu-bep")).thenReturn(new JobDetailResponse(1L, "Phụ bếp", "phu-bep",
                EmploymentType.PART_TIME, null, null, null, "## Mô tả", true, List.of(), false));
        when(jobPostingService.getBySlug("khong-co"))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy tin tuyển dụng"));

        mockMvc.perform(get("/api/v1/jobs/phu-bep"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.acceptingApplications").value(false));
        mockMvc.perform(get("/api/v1/jobs/khong-co"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy tin tuyển dụng"));
    }

    @Test
    void adminCreateParsesDeadlineAndStores() throws Exception {
        when(jobPostingService.create(any(JobPostingRequest.class))).thenReturn(adminJob(JobStatus.OPEN));

        mockMvc.perform(post("/api/v1/admin/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Phụ bếp","employmentType":"PART_TIME","deadline":"2026-10-31",
                                 "description":"## Mô tả","storeIds":[1,2]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("phu-bep"));

        ArgumentCaptor<JobPostingRequest> captor = ArgumentCaptor.forClass(JobPostingRequest.class);
        verify(jobPostingService).create(captor.capture());
        assertThat(captor.getValue().deadline()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(captor.getValue().storeIds()).containsExactly(1L, 2L);
    }

    @Test
    void adminToggleStatusListAndDelete() throws Exception {
        when(jobPostingService.setStatus(5L, JobStatus.CLOSED)).thenReturn(adminJob(JobStatus.CLOSED));
        when(jobPostingService.searchAdmin(eq(JobStatus.OPEN), eq(null), eq(0), eq(20)))
                .thenReturn(new com.banhmyking.banhmyking.dto.common.PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(patch("/api/v1/admin/jobs/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(get("/api/v1/admin/jobs").param("status", "OPEN")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/admin/jobs/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã xoá tin tuyển dụng"));
        verify(jobPostingService).delete(5L);
    }

    private static AdminJobResponse adminJob(JobStatus status) {
        return new AdminJobResponse(5L, "Phụ bếp", "phu-bep", EmploymentType.PART_TIME, null, null,
                LocalDate.of(2026, 10, 31), "## Mô tả", status, false, false,
                List.of(new StoreRef(1L, "CS01", "Cơ sở 1")), LocalDateTime.of(2026, 10, 2, 9, 0));
    }
}
```

- [ ] **Step 7: Chạy test controller, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=JobControllerTest`
Expected: COMPILATION ERROR (`JobController`, `AdminJobController` chưa tồn tại).

- [ ] **Step 8: `M/controller/JobController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.service.JobPostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Tin tuyển dụng công khai (spec D §4)")
public class JobController {

    private final JobPostingService jobPostingService;

    @GetMapping
    @Operation(summary = "Tin đang tuyển", description = "storeId: chỉ tin tuyển ở cơ sở đó hoặc tuyển toàn chuỗi.")
    public ResponseEntity<ApiResponse<List<JobSummaryResponse>>> list(@RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin tuyển dụng thành công", jobPostingService.listOpen(storeId)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Chi tiết tin", description = "Tin đóng/hết hạn vẫn xem được, acceptingApplications = false.")
    public ResponseEntity<ApiResponse<JobDetailResponse>> detail(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin tuyển dụng thành công", jobPostingService.getBySlug(slug)));
    }
}
```

- [ ] **Step 9: `M/controller/AdminJobController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobStatusRequest;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.service.JobPostingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin jobs", description = "Soạn tin tuyển dụng (chỉ ADMIN)")
public class AdminJobController {

    private final JobPostingService jobPostingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminJobResponse>>> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tin tuyển dụng thành công",
                jobPostingService.searchAdmin(status, keyword, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminJobResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin tuyển dụng thành công", jobPostingService.getAdmin(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminJobResponse>> create(@RequestBody JobPostingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã tạo tin tuyển dụng", jobPostingService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminJobResponse>> update(@PathVariable Long id,
                                                                @RequestBody JobPostingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật tin tuyển dụng", jobPostingService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminJobResponse>> setStatus(@PathVariable Long id,
                                                                   @RequestBody JobStatusRequest request) {
        AdminJobResponse job = jobPostingService.setStatus(id, request.status());
        return ResponseEntity.ok(ApiResponse.ok(
                job.status() == JobStatus.OPEN ? "Đã mở lại tin tuyển dụng" : "Đã đóng tin tuyển dụng", job));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        jobPostingService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xoá tin tuyển dụng"));
    }
}
```

- [ ] **Step 10: Chạy cả hai test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest='JobPostingServiceTest,JobControllerTest'`
Expected: `Tests run: 12, Failures: 0, Errors: 0` → BUILD SUCCESS.

- [ ] **Step 11: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/job src/main/java/com/banhmyking/banhmyking/service/JobPostingService.java src/main/java/com/banhmyking/banhmyking/controller/JobController.java src/main/java/com/banhmyking/banhmyking/controller/AdminJobController.java src/test/java/com/banhmyking/banhmyking/service/JobPostingServiceTest.java src/test/java/com/banhmyking/banhmyking/controller/JobControllerTest.java
git commit -m "feat(jobs): tin tuyển dụng theo cơ sở hoặc toàn chuỗi, hạn nộp, đóng/mở — API công khai và ADMIN"
```

---

## Task 5: Lưu CV riêng tư — `CvStorageService`

**Files:**
- Create: `M/dto/job/StoredCv.java`, `M/service/CvStorageService.java`
- Modify: `.gitignore` (thêm `private-uploads/`), `src/main/resources/application.properties` (thêm `app.private-upload-dir`)
- Test: `T/service/CvStorageServiceTest.java`

**Interfaces:**
- Consumes: `BusinessException`, `ErrorCode`, `ResourceNotFoundException`.
- Produces:
  - `record StoredCv(String fileKey, String originalName, String contentType)`.
  - `CvStorageService(@Value("${app.private-upload-dir:private-uploads}") String privateUploadDir)`: `StoredCv store(MultipartFile file)` (null khi không có file; lỗi → 400 "File CV không được vượt quá 5MB" / "File CV phải là PDF, JPG hoặc PNG"), `Resource load(String fileKey)` (khoá sai dạng / không có file → 404 "Không tìm thấy file CV"); hằng `MAX_CV_BYTES`, `INVALID_TYPE`.
  - File nằm ở `<app.private-upload-dir>/cv/<uuid32>.<pdf|jpg|png>`; thư mục này **không** được map ở `WebMvcConfig`.

- [ ] **Step 1: Viết test (đỏ)**

`T/service/CvStorageServiceTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.dto.job.StoredCv;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class CvStorageServiceTest {

    static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0};

    @TempDir Path tempDir;

    private CvStorageService service;

    @BeforeEach
    void setUp() {
        service = new CvStorageService(tempDir.toString());
    }

    @Test
    void storesPdfPngJpegUnderCvFolderWithRandomKeyAndDetectedType() throws Exception {
        StoredCv pdf = service.store(new MockMultipartFile("cv", "CV Nguyễn Văn A.pdf", "application/pdf", PDF));
        assertThat(pdf.fileKey()).matches("[a-f0-9]{32}\\.pdf");
        assertThat(pdf.originalName()).isEqualTo("CV Nguyễn Văn A.pdf");
        assertThat(pdf.contentType()).isEqualTo("application/pdf");
        assertThat(Files.readAllBytes(tempDir.resolve("cv").resolve(pdf.fileKey()))).isEqualTo(PDF);

        StoredCv png = service.store(new MockMultipartFile("cv", "anh.PNG", "image/png", PNG));
        assertThat(png.fileKey()).endsWith(".png");
        assertThat(png.contentType()).isEqualTo("image/png");

        StoredCv jpg = service.store(new MockMultipartFile("cv", "C:\\fakepath\\the.jpeg", "image/jpeg", JPEG));
        assertThat(jpg.fileKey()).endsWith(".jpg");
        assertThat(jpg.contentType()).isEqualTo("image/jpeg");
        assertThat(jpg.originalName()).isEqualTo("the.jpeg");
    }

    @Test
    void missingOrEmptyFileMeansNoCv() {
        assertThat(service.store(null)).isNull();
        assertThat(service.store(new MockMultipartFile("cv", "", "application/pdf", new byte[0]))).isNull();
    }

    @Test
    void renamedExecutableIsRejectedAndNothingIsWritten() {
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.pdf", "application/pdf", EXE)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThat(Files.exists(tempDir.resolve("cv"))).isFalse();
    }

    @Test
    void extensionDeclaredTypeAndSignatureMustAgree() {
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.png", "image/png", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.pdf", "image/png", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.docx", "application/pdf", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv", "application/pdf", PDF)))
                .hasMessage("File CV phải là PDF, JPG hoặc PNG");
    }

    @Test
    void fileOverFiveMegabytesIsRejected() {
        byte[] big = Arrays.copyOf(PDF, (int) CvStorageService.MAX_CV_BYTES + 1);
        assertThatThrownBy(() -> service.store(new MockMultipartFile("cv", "cv.pdf", "application/pdf", big)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("File CV không được vượt quá 5MB");
    }

    @Test
    void loadReturnsStoredBytesAndRejectsBadOrMissingKeys() throws Exception {
        StoredCv pdf = service.store(new MockMultipartFile("cv", "cv.pdf", "application/pdf; charset=binary", PDF));

        assertThat(service.load(pdf.fileKey()).getContentAsByteArray()).isEqualTo(PDF);
        assertThatThrownBy(() -> service.load("../../application.properties"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy file CV");
        assertThatThrownBy(() -> service.load("0123456789abcdef0123456789abcdef.pdf"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.load(null)).isInstanceOf(ResourceNotFoundException.class);
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=CvStorageServiceTest`
Expected: COMPILATION ERROR (`CvStorageService`, `StoredCv` chưa tồn tại).

- [ ] **Step 3: `M/dto/job/StoredCv.java`**

```java
package com.banhmyking.banhmyking.dto.job;

/** Kết quả lưu CV: khoá file ngẫu nhiên + tên gốc (để tải về) + loại suy ra từ chữ ký file. */
public record StoredCv(String fileKey, String originalName, String contentType) {
}
```

- [ ] **Step 4: `M/service/CvStorageService.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.job.StoredCv;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * CV ứng viên là dữ liệu cá nhân (spec D §4 "Lưu CV"): nằm trong {@code <app.private-upload-dir>/cv/},
 * KHÔNG map ra /uploads/**, chỉ đọc qua GET /job-applications/{id}/cv sau khi kiểm quyền.
 * Đuôi file, content-type khai báo và chữ ký nội dung phải khớp nhau (chặn .exe đổi tên thành .pdf).
 */
@Slf4j
@Service
public class CvStorageService {

    public static final long MAX_CV_BYTES = 5L * 1024 * 1024;
    public static final String INVALID_TYPE = "File CV phải là PDF, JPG hoặc PNG";
    static final String NOT_FOUND = "Không tìm thấy file CV";
    private static final Pattern FILE_KEY = Pattern.compile("^[a-f0-9]{32}\\.(pdf|jpg|png)$");
    private static final int MAX_ORIGINAL_NAME = 255;
    private static final int SIGNATURE_BYTES = 8;

    private final Path cvDir;

    public CvStorageService(@Value("${app.private-upload-dir:private-uploads}") String privateUploadDir) {
        this.cvDir = Paths.get(privateUploadDir, "cv").toAbsolutePath().normalize();
    }

    /** @return null khi ứng viên không đính kèm CV (không bắt buộc). */
    public StoredCv store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_CV_BYTES) {
            throw invalid("File CV không được vượt quá 5MB");
        }
        CvKind kind = CvKind.fromFilename(file.getOriginalFilename());
        if (kind == null || !kind.acceptsDeclaredType(file.getContentType()) || !kind.matchesSignature(readHead(file))) {
            throw invalid(INVALID_TYPE);
        }
        String fileKey = UUID.randomUUID().toString().replace("-", "") + "." + kind.extension;
        try {
            Files.createDirectories(cvDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, cvDir.resolve(fileKey));
            }
        } catch (IOException ex) {
            log.error("Không lưu được CV {}: {}", fileKey, ex.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không thể lưu file CV, vui lòng thử lại");
        }
        return new StoredCv(fileKey, safeOriginalName(file.getOriginalFilename(), kind), kind.contentType);
    }

    public Resource load(String fileKey) {
        if (fileKey == null || !FILE_KEY.matcher(fileKey).matches()) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        Path path = cvDir.resolve(fileKey).normalize();
        if (!path.startsWith(cvDir) || !Files.isReadable(path)) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return new FileSystemResource(path);
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(SIGNATURE_BYTES);
        } catch (IOException ex) {
            return new byte[0];
        }
    }

    /** Chỉ giữ tên file (bỏ đường dẫn "C:\fakepath\…"), bỏ ký tự điều khiển và dấu nháy kép. */
    private static String safeOriginalName(String original, CvKind kind) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (name.isEmpty()) {
            name = "cv." + kind.extension;
        }
        return name.length() > MAX_ORIGINAL_NAME ? name.substring(name.length() - MAX_ORIGINAL_NAME) : name;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    enum CvKind {
        PDF("pdf", "application/pdf", Set.of("pdf"), Set.of("application/pdf"),
                new byte[] {'%', 'P', 'D', 'F'}),
        PNG("png", "image/png", Set.of("png"), Set.of("image/png"),
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}),
        JPEG("jpg", "image/jpeg", Set.of("jpg", "jpeg"), Set.of("image/jpeg", "image/jpg", "image/pjpeg"),
                new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});

        final String extension;
        final String contentType;
        private final Set<String> extensions;
        private final Set<String> declaredTypes;
        private final byte[] signature;

        CvKind(String extension, String contentType, Set<String> extensions, Set<String> declaredTypes,
               byte[] signature) {
            this.extension = extension;
            this.contentType = contentType;
            this.extensions = extensions;
            this.declaredTypes = declaredTypes;
            this.signature = signature;
        }

        static CvKind fromFilename(String filename) {
            if (filename == null || filename.lastIndexOf('.') < 0) {
                return null;
            }
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            for (CvKind kind : values()) {
                if (kind.extensions.contains(ext)) {
                    return kind;
                }
            }
            return null;
        }

        boolean acceptsDeclaredType(String declared) {
            if (declared == null) {
                return false;
            }
            String base = declared.split(";")[0].trim().toLowerCase(Locale.ROOT);
            return declaredTypes.contains(base);
        }

        boolean matchesSignature(byte[] head) {
            return head.length >= signature.length
                    && Arrays.equals(Arrays.copyOf(head, signature.length), signature);
        }
    }
}
```

- [ ] **Step 5: Cấu hình + `.gitignore`**

Thêm cuối `src/main/resources/application.properties`:

```properties

# CV ứng viên (dữ liệu cá nhân): thư mục riêng, KHÔNG map ra /uploads/**, sao lưu cùng DB, không đưa vào git
app.private-upload-dir=private-uploads
```

Trong `.gitignore`, ngay dưới dòng `uploads/` thêm dòng:

```
private-uploads/
```

- [ ] **Step 6: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=CvStorageServiceTest`
Expected: `Tests run: 6, Failures: 0, Errors: 0`.

Kiểm tra: `git check-ignore -v private-uploads/cv/x.pdf` → in ra dòng `.gitignore:…:private-uploads/` (lệnh chỉ đọc).

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add .gitignore src/main/resources/application.properties src/main/java/com/banhmyking/banhmyking/dto/job/StoredCv.java src/main/java/com/banhmyking/banhmyking/service/CvStorageService.java src/test/java/com/banhmyking/banhmyking/service/CvStorageServiceTest.java
git commit -m "feat(jobs): lưu CV trong thư mục riêng private-uploads, kiểm đuôi + loại + chữ ký file, giới hạn 5MB"
```

---

## Task 6: Hồ sơ ứng tuyển — `JobApplicationService` (nộp + xử lý theo phạm vi) + email xác nhận

**Files:**
- Create: `M/dto/job/JobApplicationForm.java`, `M/dto/job/UpdateApplicationRequest.java`, `M/dto/job/JobApplicationResponse.java`, `M/dto/job/CvFile.java`
- Create: `M/service/JobApplicationService.java`
- Modify: `M/service/EmailService.java`, `M/service/impl/EmailServiceImpl.java` (thêm `sendApplicationConfirmation`)
- Test: `T/service/JobApplicationServiceTest.java`

**Interfaces:**
- Consumes: `JobPostingService.requireBySlug(String)`, `JobPostingService.receivingStores(JobPosting)` (Task 4); `CvStorageService.store/load`, `StoredCv` (Task 5); `SubmissionRateLimiter.check/record`, `ContactFields.*` (Task 2); `JobApplicationRepository` (Task 1); `StoreAccessGuard.resolveStoreFilter(User, Long)`, `StoreAccessGuard.scopedStoreId(User)`; `NotFoundMessages.userById(Long)`.
- Produces:
  - `record JobApplicationForm(Long storeId, String fullName, String phone, String email, String message, String website)` — `website` là ô bẫy bot.
  - `record UpdateApplicationRequest(ApplicationStatus status, String internalNote)` — trường null = không đổi; `internalNote` rỗng = xoá ghi chú.
  - `record JobApplicationResponse(Long id, Long jobId, String jobTitle, String jobSlug, Long storeId, String storeName, String fullName, String phone, String email, String message, boolean hasCv, String cvOriginalName, ApplicationStatus status, String internalNote, String handledByName, LocalDateTime handledAt, LocalDateTime createdAt)` + `static from(JobApplication)`.
  - `record CvFile(Resource resource, String originalName, String contentType)`.
  - `EmailService.sendApplicationConfirmation(String to, String fullName, String jobTitle, String storeName)`.
  - `JobApplicationService(JobPostingService, JobApplicationRepository, UserRepository, StoreAccessGuard, CvStorageService, SubmissionRateLimiter, EmailService, Clock)`:
    - `boolean submit(String slug, JobApplicationForm form, MultipartFile cv, String clientIp)` — `false` = ô bẫy (không lưu).
    - `PageResponse<JobApplicationResponse> search(Long actorId, Long jobId, Long storeId, ApplicationStatus status, int page, int size)`
    - `JobApplicationResponse get(Long actorId, Long id)`, `JobApplicationResponse update(Long actorId, Long id, UpdateApplicationRequest request)`, `CvFile loadCv(Long actorId, Long id)`, `long countNew(Long actorId)`.
    - Ngoài phạm vi cơ sở → 404 "Không tìm thấy hồ sơ ứng tuyển"; vai trò khác MANAGER/ADMIN → 403.

- [ ] **Step 1: Viết test (đỏ)**

`T/service/JobApplicationServiceTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.CvFile;
import com.banhmyking.banhmyking.dto.job.JobApplicationForm;
import com.banhmyking.banhmyking.dto.job.JobApplicationResponse;
import com.banhmyking.banhmyking.dto.job.StoredCv;
import com.banhmyking.banhmyking.dto.job.UpdateApplicationRequest;
import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.JobApplicationRepository;
import com.banhmyking.banhmyking.repository.JobPostingRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.security.SubmissionRateLimiter;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);
    private static final String IP = "10.0.0.9";

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private UserRepository userRepository;
    @Mock private CvStorageService cvStorageService;
    @Mock private EmailService emailService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM);
    private JobApplicationService service;
    private Store storeA;
    private Store storeB;
    private JobPosting job;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(new JobPostingService(jobPostingRepository, storeRepository, clock),
                jobApplicationRepository, userRepository, new StoreAccessGuard(), cvStorageService,
                new SubmissionRateLimiter(clock), emailService, clock);
        storeA = store(1L, "CS-A");
        storeB = store(2L, "CS-B");
        job = new JobPosting();
        job.setId(10L);
        job.setTitle("Phụ bếp ca tối");
        job.setSlug("phu-bep");
        job.setEmploymentType(EmploymentType.PART_TIME);
        job.setDescription("Mô tả");
        job.getStores().add(storeA);
    }

    // ------------------------------------------------------------------ nộp hồ sơ

    @Test
    void honeypotReturnsFalseWithoutTouchingAnything() {
        boolean saved = service.submit("phu-bep",
                new JobApplicationForm(1L, "Bot", "0901234567", null, null, "http://spam.example"), null, IP);

        assertThat(saved).isFalse();
        verifyNoInteractions(jobPostingRepository, jobApplicationRepository, cvStorageService, emailService);
    }

    @Test
    void closedOrExpiredPostingIsRejected() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));

        job.setStatus(JobStatus.CLOSED);
        assertThatThrownBy(() -> service.submit("phu-bep", form(1L, "0901234567"), null, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tin tuyển dụng đã hết hạn nhận hồ sơ");

        job.setStatus(JobStatus.OPEN);
        job.setDeadline(LocalDate.of(2026, 10, 1));
        assertThatThrownBy(() -> service.submit("phu-bep", form(1L, "0901234567"), null, IP))
                .hasMessage("Tin tuyển dụng đã hết hạn nhận hồ sơ");
    }

    @Test
    void requiredFieldsAndStoreOfPosting() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.submit("phu-bep",
                new JobApplicationForm(1L, " ", "0901234567", null, null, null), null, IP))
                .hasMessage("Vui lòng nhập họ tên");
        assertThatThrownBy(() -> service.submit("phu-bep",
                new JobApplicationForm(1L, "An", "", null, null, null), null, IP))
                .hasMessage("Vui lòng nhập số điện thoại");
        assertThatThrownBy(() -> service.submit("phu-bep", form(null, "0901234567"), null, IP))
                .hasMessage("Vui lòng chọn cơ sở muốn làm việc");
        assertThatThrownBy(() -> service.submit("phu-bep", form(2L, "0901234567"), null, IP))
                .hasMessage("Cơ sở không nhận hồ sơ cho vị trí này");
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    void chainWidePostingAcceptsAnyActiveStore() {
        job.getStores().clear();
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(storeA, storeB));

        assertThat(service.submit("phu-bep", form(2L, "0901234567"), null, IP)).isTrue();

        ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(saved.capture());
        assertThat(saved.getValue().getStore()).isSameAs(storeB);
    }

    @Test
    void duplicatePhoneWithin24HoursIsRejectedBeforeStoringCv() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobPostingIdAndPhoneAndCreatedAtAfter(10L, "0901234567",
                NOW.minusHours(24))).thenReturn(true);

        assertThatThrownBy(() -> service.submit("phu-bep", form(1L, "090 123 4567"), null, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bạn đã nộp hồ sơ cho vị trí này");
        verifyNoInteractions(cvStorageService);
    }

    @Test
    void successSavesNormalisedFieldsCvAndIpThenSendsConfirmation() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        when(cvStorageService.store(cv)).thenReturn(
                new StoredCv("0123456789abcdef0123456789abcdef.pdf", "cv.pdf", "application/pdf"));

        boolean saved = service.submit("phu-bep", new JobApplicationForm(1L, "  Nguyễn Văn An ", "090 123 4567",
                "an@banhmy.vn", "Em làm được ca tối", null), cv, IP);

        assertThat(saved).isTrue();
        ArgumentCaptor<JobApplication> captor = ArgumentCaptor.forClass(JobApplication.class);
        verify(jobApplicationRepository).save(captor.capture());
        JobApplication app = captor.getValue();
        assertThat(app.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(app.getPhone()).isEqualTo("0901234567");
        assertThat(app.getStore()).isSameAs(storeA);
        assertThat(app.getJobPosting()).isSameAs(job);
        assertThat(app.getCvFileKey()).isEqualTo("0123456789abcdef0123456789abcdef.pdf");
        assertThat(app.getCvContentType()).isEqualTo("application/pdf");
        assertThat(app.getClientIp()).isEqualTo(IP);
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.NEW);
        verify(emailService).sendApplicationConfirmation("an@banhmy.vn", "Nguyễn Văn An", "Phụ bếp ca tối",
                "Cơ sở CS-A");
    }

    @Test
    void sixthSuccessfulSubmissionFromSameIpWithinAnHourIs429() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        for (int i = 0; i < 5; i++) {
            service.submit("phu-bep", form(1L, "090123456" + i), null, IP);
        }

        assertThatThrownBy(() -> service.submit("phu-bep", form(1L, "0987654321"), null, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bạn thao tác quá nhanh, vui lòng thử lại sau")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
        verify(emailService, never()).sendApplicationConfirmation(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------ xử lý hồ sơ

    @Test
    void managerListIsForcedToOwnStoreAndOtherStoreFilterIs404() {
        User manager = user(50L, RoleName.MANAGER, storeA);
        when(userRepository.findById(50L)).thenReturn(Optional.of(manager));
        when(jobApplicationRepository.search(isNull(), eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(application(100L, storeA))));

        PageResponse<JobApplicationResponse> page = service.search(50L, null, null, null, 0, 20);

        assertThat(page.content()).extracting(JobApplicationResponse::storeId).containsExactly(1L);
        assertThatThrownBy(() -> service.search(50L, null, 2L, null, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void managerGets404ForApplicationAndCvOfAnotherStore() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, RoleName.MANAGER, storeA)));
        when(jobApplicationRepository.findById(200L)).thenReturn(Optional.of(application(200L, storeB)));

        assertThatThrownBy(() -> service.get(50L, 200L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy hồ sơ ứng tuyển");
        assertThatThrownBy(() -> service.loadCv(50L, 200L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.update(50L, 200L, new UpdateApplicationRequest(ApplicationStatus.HIRED, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(cvStorageService);
    }

    @Test
    void staffAndCustomerAreForbidden() {
        when(userRepository.findById(60L)).thenReturn(Optional.of(user(60L, RoleName.STAFF, storeA)));

        assertThatThrownBy(() -> service.countNew(60L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void updateStatusRecordsHandlerAndNote() {
        User admin = user(1L, RoleName.ADMIN, null);
        JobApplication app = application(300L, storeB);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(jobApplicationRepository.findById(300L)).thenReturn(Optional.of(app));
        when(jobApplicationRepository.save(app)).thenReturn(app);

        JobApplicationResponse updated = service.update(1L, 300L,
                new UpdateApplicationRequest(ApplicationStatus.CONTACTED, "  Đã gọi, hẹn thứ 2 "));

        assertThat(updated.status()).isEqualTo(ApplicationStatus.CONTACTED);
        assertThat(updated.internalNote()).isEqualTo("Đã gọi, hẹn thứ 2");
        assertThat(app.getHandledBy()).isSameAs(admin);
        assertThat(app.getHandledAt()).isEqualTo(NOW);
    }

    @Test
    void loadCvReturnsFileOr404WhenNoCv() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, RoleName.ADMIN, null)));
        JobApplication withCv = application(400L, storeA);
        withCv.setCvFileKey("0123456789abcdef0123456789abcdef.pdf");
        withCv.setCvOriginalName("CV An.pdf");
        withCv.setCvContentType("application/pdf");
        when(jobApplicationRepository.findById(400L)).thenReturn(Optional.of(withCv));
        when(jobApplicationRepository.findById(401L)).thenReturn(Optional.of(application(401L, storeA)));
        when(cvStorageService.load("0123456789abcdef0123456789abcdef.pdf"))
                .thenReturn(new ByteArrayResource(new byte[] {'%', 'P', 'D', 'F'}));

        CvFile file = service.loadCv(1L, 400L);

        assertThat(file.originalName()).isEqualTo("CV An.pdf");
        assertThat(file.contentType()).isEqualTo("application/pdf");
        assertThatThrownBy(() -> service.loadCv(1L, 401L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Hồ sơ này không có CV");
    }

    @Test
    void countNewUsesActorScope() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, RoleName.MANAGER, storeA)));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, RoleName.ADMIN, null)));
        when(jobApplicationRepository.countByStatusInScope(ApplicationStatus.NEW, 1L)).thenReturn(3L);
        when(jobApplicationRepository.countByStatusInScope(ApplicationStatus.NEW, null)).thenReturn(7L);

        assertThat(service.countNew(50L)).isEqualTo(3L);
        assertThat(service.countNew(1L)).isEqualTo(7L);
    }

    // ----------------------------------------------------------------------- helpers

    private static JobApplicationForm form(Long storeId, String phone) {
        return new JobApplicationForm(storeId, "Nguyễn Văn An", phone, null, null, null);
    }

    private static Store store(Long id, String code) {
        Store store = new Store();
        store.setId(id);
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ");
        return store;
    }

    private static User user(Long id, RoleName role, Store store) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStore(store);
        user.setFullName("Người dùng " + id);
        return user;
    }

    private JobApplication application(Long id, Store store) {
        JobApplication app = new JobApplication();
        app.setId(id);
        app.setJobPosting(job);
        app.setStore(store);
        app.setFullName("Ứng viên " + id);
        app.setPhone("0901234567");
        return app;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=JobApplicationServiceTest`
Expected: COMPILATION ERROR (`JobApplicationService`, `JobApplicationForm`, `UpdateApplicationRequest`, `JobApplicationResponse`, `CvFile`, `EmailService.sendApplicationConfirmation` chưa tồn tại).

- [ ] **Step 3: DTO trong `M/dto/job/`**

`JobApplicationForm.java`:

```java
package com.banhmyking.banhmyking.dto.job;

/**
 * Trường văn bản của form ứng tuyển (multipart). Kiểm ở JobApplicationService.
 * website là ô bẫy bot: người thật không thấy ô này nên luôn để trống.
 */
public record JobApplicationForm(
        Long storeId,
        String fullName,
        String phone,
        String email,
        String message,
        String website
) {
}
```

`UpdateApplicationRequest.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.enums.ApplicationStatus;

/** PATCH hồ sơ: trường null = giữ nguyên; internalNote rỗng = xoá ghi chú. */
public record UpdateApplicationRequest(ApplicationStatus status, String internalNote) {
}
```

`JobApplicationResponse.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import java.time.LocalDateTime;

/** Hồ sơ cho màn quản lý — không lộ khoá file CV, chỉ báo có/không và tên gốc. */
public record JobApplicationResponse(
        Long id,
        Long jobId,
        String jobTitle,
        String jobSlug,
        Long storeId,
        String storeName,
        String fullName,
        String phone,
        String email,
        String message,
        boolean hasCv,
        String cvOriginalName,
        ApplicationStatus status,
        String internalNote,
        String handledByName,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {
    public static JobApplicationResponse from(JobApplication app) {
        return new JobApplicationResponse(app.getId(), app.getJobPosting().getId(), app.getJobPosting().getTitle(),
                app.getJobPosting().getSlug(), app.getStore().getId(), app.getStore().getName(), app.getFullName(),
                app.getPhone(), app.getEmail(), app.getMessage(), app.hasCv(), app.getCvOriginalName(),
                app.getStatus(), app.getInternalNote(),
                app.getHandledBy() == null ? null : app.getHandledBy().getFullName(), app.getHandledAt(),
                app.getCreatedAt());
    }
}
```

`CvFile.java`:

```java
package com.banhmyking.banhmyking.dto.job;

import org.springframework.core.io.Resource;

/** File CV đã qua kiểm quyền, sẵn sàng trả về dạng tải xuống. */
public record CvFile(Resource resource, String originalName, String contentType) {
}
```

- [ ] **Step 4: `EmailService` — thêm phương thức**

Trong `M/service/EmailService.java`, thêm vào trong interface (sau `sendPasswordResetEmail`):

```java
    /** Xác nhận đã nhận hồ sơ ứng tuyển (spec D §4). Lỗi SMTP chỉ được log, KHÔNG ném ra ngoài. */
    void sendApplicationConfirmation(String to, String fullName, String jobTitle, String storeName);
```

Trong `M/service/impl/EmailServiceImpl.java`, thêm sau phương thức `sendPasswordResetEmail`:

```java
    @Override
    public void sendApplicationConfirmation(String to, String fullName, String jobTitle, String storeName) {
        String body = "Cảm ơn bạn đã ứng tuyển vị trí <strong>%s</strong> tại <strong>%s</strong>. "
                .formatted(HtmlUtils.htmlEscape(jobTitle), HtmlUtils.htmlEscape(storeName))
                + "Cửa hàng sẽ xem hồ sơ và liên hệ với bạn qua số điện thoại đã đăng ký trong thời gian sớm nhất.";
        send(to, "Bánh Mỳ King đã nhận hồ sơ ứng tuyển của bạn", buildInfoHtml(fullName, body),
                "xác nhận ứng tuyển");
    }

    /** Email chỉ có lời nhắn (không nút bấm). body đã được escape phần dữ liệu người dùng. */
    private String buildInfoHtml(String fullName, String body) {
        return """
                <div style="font-family:Arial,'Helvetica Neue',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#2b1a0e">
                  <h2 style="color:#b45309;margin:0 0 8px">BÁNH MỲ KING</h2>
                  <p>Xin chào <strong>%s</strong>,</p>
                  <p>%s</p>
                  <p style="font-size:13px;color:#6b7280">Email này được gửi tự động, vui lòng không trả lời.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(fullName == null ? "" : fullName), body);
    }
```

- [ ] **Step 5: `M/service/JobApplicationService.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.CvFile;
import com.banhmyking.banhmyking.dto.job.JobApplicationForm;
import com.banhmyking.banhmyking.dto.job.JobApplicationResponse;
import com.banhmyking.banhmyking.dto.job.StoredCv;
import com.banhmyking.banhmyking.dto.job.UpdateApplicationRequest;
import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.JobApplicationRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.security.SubmissionRateLimiter;
import com.banhmyking.banhmyking.util.ContactFields;
import com.banhmyking.banhmyking.util.PageableFactory;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Hồ sơ ứng tuyển (spec D §4): nộp công khai có chống spam; MANAGER chỉ xử lý hồ sơ của cơ sở mình,
 * ADMIN mọi cơ sở; ngoài phạm vi → 404 như dự án con A.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobApplicationService {

    static final String NOT_FOUND = "Không tìm thấy hồ sơ ứng tuyển";
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final JobPostingService jobPostingService;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserRepository userRepository;
    private final StoreAccessGuard storeAccessGuard;
    private final CvStorageService cvStorageService;
    private final SubmissionRateLimiter rateLimiter;
    private final EmailService emailService;
    private final Clock clock;

    // ------------------------------------------------------------------ nộp hồ sơ (công khai)

    /** @return false khi dính ô bẫy bot — controller vẫn trả 200 như thật, không lưu gì. */
    @Transactional
    public boolean submit(String slug, JobApplicationForm form, MultipartFile cv, String clientIp) {
        if (ContactFields.trimToNull(form.website()) != null) {
            log.info("Bỏ qua hồ sơ ứng tuyển dính ô bẫy bot từ IP {}", clientIp);
            return false;
        }
        rateLimiter.check(clientIp);

        JobPosting job = jobPostingService.requireBySlug(slug);
        if (!job.acceptsApplicationsOn(LocalDate.now(clock))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Tin tuyển dụng đã hết hạn nhận hồ sơ");
        }
        String fullName = ContactFields.requireText(form.fullName(), 100, "Vui lòng nhập họ tên",
                "Họ tên tối đa 100 ký tự");
        String phone = ContactFields.phone(form.phone());
        if (phone == null) {
            throw invalid("Vui lòng nhập số điện thoại");
        }
        String email = ContactFields.email(form.email());
        String message = ContactFields.optionalText(form.message(), 2000, "Lời nhắn tối đa 2000 ký tự");
        if (form.storeId() == null) {
            throw invalid("Vui lòng chọn cơ sở muốn làm việc");
        }
        Store store = jobPostingService.receivingStores(job).stream()
                .filter(s -> s.getId().equals(form.storeId()))
                .findFirst()
                .orElseThrow(() -> invalid("Cơ sở không nhận hồ sơ cho vị trí này"));
        LocalDateTime now = LocalDateTime.now(clock);
        if (jobApplicationRepository.existsByJobPostingIdAndPhoneAndCreatedAtAfter(job.getId(), phone,
                now.minusHours(24))) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Bạn đã nộp hồ sơ cho vị trí này");
        }
        // Lưu CV sau cùng: lỗi nhập liệu phía trên không để lại file mồ côi.
        StoredCv storedCv = cvStorageService.store(cv);

        JobApplication application = new JobApplication();
        application.setJobPosting(job);
        application.setStore(store);
        application.setFullName(fullName);
        application.setPhone(phone);
        application.setEmail(email);
        application.setMessage(message);
        application.setClientIp(clientIp);
        if (storedCv != null) {
            application.setCvFileKey(storedCv.fileKey());
            application.setCvOriginalName(storedCv.originalName());
            application.setCvContentType(storedCv.contentType());
        }
        jobApplicationRepository.save(application);
        rateLimiter.record(clientIp);

        if (email != null) {
            emailService.sendApplicationConfirmation(email, fullName, job.getTitle(), store.getName());
        }
        return true;
    }

    // ------------------------------------------------------------------ xử lý hồ sơ (MANAGER / ADMIN)

    @Transactional(readOnly = true)
    public PageResponse<JobApplicationResponse> search(Long actorId, Long jobId, Long storeId,
                                                       ApplicationStatus status, int page, int size) {
        User actor = requireInboxActor(actorId);
        Long scopedStoreId = storeAccessGuard.resolveStoreFilter(actor, storeId);
        return PageResponse.from(jobApplicationRepository.search(jobId, scopedStoreId, status,
                        PageableFactory.of(page, size, NEWEST_FIRST))
                .map(JobApplicationResponse::from));
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse get(Long actorId, Long id) {
        return JobApplicationResponse.from(requireInScope(requireInboxActor(actorId), id));
    }

    @Transactional
    public JobApplicationResponse update(Long actorId, Long id, UpdateApplicationRequest request) {
        User actor = requireInboxActor(actorId);
        JobApplication app = requireInScope(actor, id);
        if (request.status() != null && request.status() != app.getStatus()) {
            app.setStatus(request.status());
            if (request.status() != ApplicationStatus.NEW) {
                app.setHandledBy(actor);
                app.setHandledAt(LocalDateTime.now(clock));
            }
        }
        if (request.internalNote() != null) {
            app.setInternalNote(ContactFields.optionalText(request.internalNote(), 2000,
                    "Ghi chú nội bộ tối đa 2000 ký tự"));
        }
        return JobApplicationResponse.from(jobApplicationRepository.save(app));
    }

    @Transactional(readOnly = true)
    public CvFile loadCv(Long actorId, Long id) {
        JobApplication app = requireInScope(requireInboxActor(actorId), id);
        if (!app.hasCv()) {
            throw new ResourceNotFoundException("Hồ sơ này không có CV");
        }
        return new CvFile(cvStorageService.load(app.getCvFileKey()), app.getCvOriginalName(),
                app.getCvContentType());
    }

    @Transactional(readOnly = true)
    public long countNew(Long actorId) {
        User actor = requireInboxActor(actorId);
        return jobApplicationRepository.countByStatusInScope(ApplicationStatus.NEW,
                storeAccessGuard.scopedStoreId(actor));
    }

    // ------------------------------------------------------------------ nội bộ

    private User requireInboxActor(Long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(actorId)));
        if (actor.getRole() != RoleName.MANAGER && actor.getRole() != RoleName.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Chỉ quản lý cơ sở hoặc quản trị viên mới xem được hồ sơ ứng tuyển");
        }
        return actor;
    }

    /** MANAGER chạm hồ sơ cơ sở khác → 404 (không lộ hồ sơ có tồn tại). */
    private JobApplication requireInScope(User actor, Long id) {
        JobApplication app = jobApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Long ownStoreId = storeAccessGuard.scopedStoreId(actor);
        if (ownStoreId != null && !ownStoreId.equals(app.getStore().getId())) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return app;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
```

- [ ] **Step 6: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest='JobApplicationServiceTest,AuthServiceImplTest'`
Expected: `JobApplicationServiceTest` 13 test xanh; `AuthServiceImplTest` vẫn xanh (mock `EmailService` không bị ảnh hưởng bởi phương thức mới) → BUILD SUCCESS.

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/job src/main/java/com/banhmyking/banhmyking/service/JobApplicationService.java src/main/java/com/banhmyking/banhmyking/service/EmailService.java src/main/java/com/banhmyking/banhmyking/service/impl/EmailServiceImpl.java src/test/java/com/banhmyking/banhmyking/service/JobApplicationServiceTest.java
git commit -m "feat(jobs): nộp hồ sơ ứng tuyển (ô bẫy, chống trùng 24h, 5 lần/giờ/IP, email xác nhận) và xử lý hồ sơ theo cơ sở"
```

---

## Task 7: API hồ sơ — nộp multipart công khai + quản lý hồ sơ / tải CV (MANAGER, ADMIN)

**Files:**
- Create: `M/controller/JobApplyController.java`, `M/controller/JobApplicationController.java`
- Test: `T/controller/JobApplicationControllerTest.java`

**Interfaces:**
- Consumes: `JobApplicationService.submit/search/get/update/loadCv/countNew`, `JobApplicationForm`, `UpdateApplicationRequest`, `JobApplicationResponse`, `CvFile` (Task 6); `ClientIpResolver.resolve(HttpServletRequest)` (Task 2); `SecurityUtils.requireUserId(UserDetails)`.
- Produces:
  - `POST /api/v1/jobs/{slug}/applications` (multipart: `storeId`, `fullName`, `phone`, `email`, `message`, `website`, `cv`) → luôn 200 `{"message":"Đã gửi hồ sơ ứng tuyển. Cửa hàng sẽ liên hệ với bạn sớm."}` khi thành công hoặc dính ô bẫy.
  - `GET /api/v1/job-applications?jobId&storeId&status&page=0&size=20`, `GET /api/v1/job-applications/count-new` (`data` = số), `GET /api/v1/job-applications/{id}`, `PATCH /api/v1/job-applications/{id}` (JSON `{status, internalNote}`), `GET /api/v1/job-applications/{id}/cv` (file, `Content-Disposition: attachment`, `X-Content-Type-Options: nosniff`).
  - Hằng `JobApplyController.SUBMITTED`.

- [ ] **Step 1: Viết test controller (đỏ)**

`T/controller/JobApplicationControllerTest.java`:

```java
package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.CvFile;
import com.banhmyking.banhmyking.dto.job.JobApplicationForm;
import com.banhmyking.banhmyking.dto.job.JobApplicationResponse;
import com.banhmyking.banhmyking.dto.job.UpdateApplicationRequest;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.security.SubmissionRateLimiter;
import com.banhmyking.banhmyking.service.JobApplicationService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class JobApplicationControllerTest {

    private static final UserDetails MANAGER = User.withUsername("50").password("x").authorities("ROLE_MANAGER").build();
    private static final byte[] PDF = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    private static final String SUBMITTED = "Đã gửi hồ sơ ứng tuyển. Cửa hàng sẽ liên hệ với bạn sớm.";

    @Mock private JobApplicationService jobApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new JobApplyController(jobApplicationService, new ClientIpResolver(false)),
                        new JobApplicationController(jobApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(MANAGER, null, MANAGER.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void multipartSubmissionBindsFieldsCvAndClientIp() throws Exception {
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf", PDF);
        when(jobApplicationService.submit(eq("phu-bep"), any(JobApplicationForm.class), any(MultipartFile.class),
                eq("10.1.2.3"))).thenReturn(true);

        mockMvc.perform(multipart("/api/v1/jobs/phu-bep/applications").file(cv)
                        .param("storeId", "3")
                        .param("fullName", "Nguyễn Văn An")
                        .param("phone", "0901234567")
                        .param("email", "an@banhmy.vn")
                        .param("message", "Em làm được ca tối")
                        .with(request -> {
                            request.setRemoteAddr("10.1.2.3");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SUBMITTED))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<JobApplicationForm> form = ArgumentCaptor.forClass(JobApplicationForm.class);
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        verify(jobApplicationService).submit(eq("phu-bep"), form.capture(), file.capture(), eq("10.1.2.3"));
        assertThat(form.getValue().storeId()).isEqualTo(3L);
        assertThat(form.getValue().fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(form.getValue().website()).isNull();
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("cv.pdf");
    }

    @Test
    void honeypotResponseIsIdenticalToRealSubmission() throws Exception {
        when(jobApplicationService.submit(eq("phu-bep"), any(JobApplicationForm.class), isNull(), any()))
                .thenReturn(false);

        mockMvc.perform(multipart("/api/v1/jobs/phu-bep/applications")
                        .param("fullName", "Bot").param("website", "http://spam.example"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SUBMITTED));
    }

    @Test
    void rateLimitValidationAndBadParamsMapToStatusCodes() throws Exception {
        when(jobApplicationService.submit(eq("a"), any(JobApplicationForm.class), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, SubmissionRateLimiter.MESSAGE));
        when(jobApplicationService.submit(eq("b"), any(JobApplicationForm.class), isNull(), any()))
                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR, "File CV phải là PDF, JPG hoặc PNG"));

        mockMvc.perform(multipart("/api/v1/jobs/a/applications").param("fullName", "An"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.message").value("Bạn thao tác quá nhanh, vui lòng thử lại sau"));
        mockMvc.perform(multipart("/api/v1/jobs/b/applications").param("fullName", "An"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File CV phải là PDF, JPG hoặc PNG"));
        mockMvc.perform(multipart("/api/v1/jobs/c/applications").param("storeId", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managerEndpointsPassActorId() throws Exception {
        when(jobApplicationService.search(50L, 10L, null, ApplicationStatus.NEW, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true));
        when(jobApplicationService.countNew(50L)).thenReturn(4L);
        when(jobApplicationService.get(50L, 7L)).thenReturn(response());
        when(jobApplicationService.update(eq(50L), eq(7L), any(UpdateApplicationRequest.class))).thenReturn(response());

        mockMvc.perform(get("/api/v1/job-applications").param("jobId", "10").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].fullName").value("Nguyễn Văn An"));
        mockMvc.perform(get("/api/v1/job-applications/count-new"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(4));
        mockMvc.perform(get("/api/v1/job-applications/7")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/job-applications/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONTACTED\",\"internalNote\":\"Đã gọi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã cập nhật hồ sơ"));

        ArgumentCaptor<UpdateApplicationRequest> captor = ArgumentCaptor.forClass(UpdateApplicationRequest.class);
        verify(jobApplicationService).update(eq(50L), eq(7L), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(ApplicationStatus.CONTACTED);
        assertThat(captor.getValue().internalNote()).isEqualTo("Đã gọi");
    }

    @Test
    void cvDownloadIsAttachmentWithNosniff() throws Exception {
        when(jobApplicationService.loadCv(50L, 7L))
                .thenReturn(new CvFile(new ByteArrayResource(PDF), "CV Nguyễn Văn An.pdf", "application/pdf"));

        mockMvc.perform(get("/api/v1/job-applications/7/cv"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("UTF-8")))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void outOfScopeCvIs404Json() throws Exception {
        when(jobApplicationService.loadCv(50L, 8L)).thenThrow(new ResourceNotFoundException("Không tìm thấy hồ sơ ứng tuyển"));

        mockMvc.perform(get("/api/v1/job-applications/8/cv"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void anonymousCallToManagerEndpointIs401FromService() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/job-applications/count-new"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(jobApplicationService);
    }

    private static JobApplicationResponse response() {
        return new JobApplicationResponse(7L, 10L, "Phụ bếp", "phu-bep", 1L, "Cơ sở 1", "Nguyễn Văn An",
                "0901234567", null, null, true, "cv.pdf", ApplicationStatus.NEW, null, null, null,
                LocalDateTime.of(2026, 10, 2, 9, 0));
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=JobApplicationControllerTest`
Expected: COMPILATION ERROR (`JobApplyController`, `JobApplicationController` chưa tồn tại).

- [ ] **Step 3: `M/controller/JobApplyController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.job.JobApplicationForm;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.service.JobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nộp hồ sơ công khai (spec D §4). Mọi trường required = false để thiếu trường ra câu lỗi tiếng Việt
 * từ service thay vì lỗi 500. Thành công thật và ô bẫy bot trả cùng một phản hồi.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Job applications", description = "Nộp hồ sơ ứng tuyển công khai")
public class JobApplyController {

    public static final String SUBMITTED = "Đã gửi hồ sơ ứng tuyển. Cửa hàng sẽ liên hệ với bạn sớm.";

    private final JobApplicationService jobApplicationService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping(value = "/{slug}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nộp hồ sơ ứng tuyển",
            description = "multipart: storeId, fullName, phone, email, message, cv (PDF/JPG/PNG ≤ 5MB, tuỳ chọn). "
                    + "Tối đa 5 lần/giờ/IP (chung với phản hồi) → 429.")
    public ResponseEntity<ApiResponse<Void>> apply(
            @PathVariable String slug,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String website,
            @RequestPart(value = "cv", required = false) MultipartFile cv,
            HttpServletRequest request) {
        jobApplicationService.submit(slug, new JobApplicationForm(storeId, fullName, phone, email, message, website),
                cv, clientIpResolver.resolve(request));
        return ResponseEntity.ok(ApiResponse.ok(SUBMITTED));
    }
}
```

- [ ] **Step 4: `M/controller/JobApplicationController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.CvFile;
import com.banhmyking.banhmyking.dto.job.JobApplicationResponse;
import com.banhmyking.banhmyking.dto.job.UpdateApplicationRequest;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.JobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Xử lý hồ sơ ứng tuyển: MANAGER cơ sở mình, ADMIN mọi cơ sở (spec D §4, §6). */
@RestController
@RequestMapping("/api/v1/job-applications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@Tag(name = "Job applications (quản lý)", description = "Hồ sơ ứng tuyển theo phạm vi cơ sở")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @GetMapping
    @Operation(summary = "Danh sách hồ sơ", description = "MANAGER bị ép storeId = cơ sở mình; cơ sở khác → 404.")
    public ResponseEntity<ApiResponse<PageResponse<JobApplicationResponse>>> list(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hồ sơ thành công", jobApplicationService.search(
                SecurityUtils.requireUserId(principal), jobId, storeId, status, page, size)));
    }

    @GetMapping("/count-new")
    @Operation(summary = "Số hồ sơ NEW trong phạm vi (huy hiệu menu)")
    public ResponseEntity<ApiResponse<Long>> countNew(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(jobApplicationService.countNew(SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> get(@PathVariable Long id,
                                                                   @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy hồ sơ thành công",
                jobApplicationService.get(SecurityUtils.requireUserId(principal), id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Đổi trạng thái / ghi chú nội bộ")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> update(@PathVariable Long id,
                                                                      @RequestBody UpdateApplicationRequest request,
                                                                      @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật hồ sơ",
                jobApplicationService.update(SecurityUtils.requireUserId(principal), id, request)));
    }

    @GetMapping("/{id}/cv")
    @Operation(summary = "Tải CV", description = "Chỉ qua API này sau kiểm quyền; luôn tải xuống (attachment).")
    public ResponseEntity<Resource> downloadCv(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        CvFile cv = jobApplicationService.loadCv(SecurityUtils.requireUserId(principal), id);
        String fileName = cv.originalName() == null ? "cv" : cv.originalName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(cv.contentType()))
                .body(cv.resource());
    }
}
```

- [ ] **Step 5: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=JobApplicationControllerTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`.

- [ ] **Step 6: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/controller/JobApplyController.java src/main/java/com/banhmyking/banhmyking/controller/JobApplicationController.java src/test/java/com/banhmyking/banhmyking/controller/JobApplicationControllerTest.java
git commit -m "feat(jobs): API nộp hồ sơ multipart kèm CV, danh sách/xử lý hồ sơ và tải CV dạng attachment"
```

---

## Task 8: Phản hồi — `FeedbackService` (chủ đơn, cơ sở theo đơn, email admin) + API công khai và quản lý

**Files:**
- Create: `M/dto/feedback/FeedbackRequest.java`, `M/dto/feedback/UpdateFeedbackRequest.java`, `M/dto/feedback/FeedbackResponse.java`, `M/dto/feedback/FeedbackNotice.java`
- Create: `M/service/FeedbackService.java`
- Create: `M/controller/FeedbackController.java`, `M/controller/AdminFeedbackController.java`
- Modify: `M/service/EmailService.java`, `M/service/impl/EmailServiceImpl.java` (thêm `sendFeedbackNotice`)
- Test: `T/service/FeedbackServiceTest.java`, `T/controller/FeedbackControllerTest.java`

**Interfaces:**
- Consumes: `Feedback`, `FeedbackRepository` (Task 1); `ContactFields`, `SubmissionRateLimiter`, `ClientIpResolver` (Task 2); `OrderRepository.findByOrderCodeAndUserId(String, Long)`, `StoreRepository.findByIdAndDeletedFalse(Long)`, `UserRepository.findByIdAndDeletedFalse(Long)`, `UserRepository.findById(Long)`, `SiteSettingService.getPublicSettings()`, `SiteSettingKeys.CONTACT_EMAIL` (cùng package `service`), `StoreAccessGuard.resolveStoreFilter/scopedStoreId`, `EmailServiceImpl.send(...)` (private, có sẵn), `NotFoundMessages.userById(Long)`.
- Produces:
  - `record FeedbackRequest(FeedbackType type, Long storeId, String orderCode, String fullName, String phone, String email, String subject, String content, String website)`.
  - `record UpdateFeedbackRequest(FeedbackStatus status, String resolutionNote)`.
  - `record FeedbackResponse(Long id, FeedbackType type, Long storeId, String storeName, String orderCode, Long userId, String fullName, String phone, String email, String subject, String content, FeedbackStatus status, String resolutionNote, String handledByName, LocalDateTime handledAt, LocalDateTime createdAt)` + `static from(Feedback)`.
  - `record FeedbackNotice(String typeLabel, String subject, String senderName, String phone, String email, String storeName, String orderCode, String content)` + `static from(Feedback)`.
  - `EmailService.sendFeedbackNotice(String to, FeedbackNotice notice)`.
  - `FeedbackService(FeedbackRepository, OrderRepository, StoreRepository, UserRepository, StoreAccessGuard, SubmissionRateLimiter, SiteSettingService, EmailService, Clock)`: `boolean submit(Long senderId /* null = khách */, FeedbackRequest, String clientIp)`, `PageResponse<FeedbackResponse> search(Long actorId, FeedbackType type, Long storeId, FeedbackStatus status, int page, int size)`, `FeedbackResponse get(Long actorId, Long id)`, `FeedbackResponse update(Long actorId, Long id, UpdateFeedbackRequest)`, `long countNew(Long actorId)`.
  - HTTP: `POST /api/v1/feedbacks` (JSON, đăng nhập tuỳ chọn) → 200 `"Cảm ơn bạn! Phản hồi đã được gửi tới cửa hàng."`; MANAGER/ADMIN `GET /api/v1/admin/feedbacks?type&storeId&status&page=0&size=20`, `GET /api/v1/admin/feedbacks/count-new`, `GET /api/v1/admin/feedbacks/{id}`, `PATCH /api/v1/admin/feedbacks/{id}`.

- [ ] **Step 1: Viết test service (đỏ)**

`T/service/FeedbackServiceTest.java`:

```java
package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackNotice;
import com.banhmyking.banhmyking.dto.feedback.FeedbackRequest;
import com.banhmyking.banhmyking.dto.feedback.FeedbackResponse;
import com.banhmyking.banhmyking.dto.feedback.UpdateFeedbackRequest;
import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.FeedbackRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.security.SubmissionRateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);
    private static final String IP = "10.0.0.7";

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StoreRepository storeRepository;
    @Mock private UserRepository userRepository;
    @Mock private SiteSettingService siteSettingService;
    @Mock private EmailService emailService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM);
    private FeedbackService service;
    private Store storeA;
    private Store storeB;
    private User customer;

    @BeforeEach
    void setUp() {
        service = new FeedbackService(feedbackRepository, orderRepository, storeRepository, userRepository,
                new StoreAccessGuard(), new SubmissionRateLimiter(clock), siteSettingService, emailService, clock);
        storeA = store(1L, "CS-A", true);
        storeB = store(2L, "CS-B", true);
        customer = user(5L, RoleName.CUSTOMER, null);
        customer.setEmail("khach@banhmy.vn");
    }

    // ------------------------------------------------------------------ gửi phản hồi

    @Test
    void honeypotReturnsFalseWithoutSaving() {
        FeedbackRequest bot = new FeedbackRequest(FeedbackType.OTHER, null, null, "Bot", "0901234567", null,
                "Spam", "Nội dung spam đủ dài", "http://spam.example");

        assertThat(service.submit(null, bot, IP)).isFalse();
        verifyNoInteractions(feedbackRepository, orderRepository, emailService, siteSettingService);
    }

    @Test
    void anonymousCannotAttachOrder() {
        assertThatThrownBy(() -> service.submit(null, request(null, "BMK-20261002-AAAAA"), IP))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy đơn hàng");
        verifyNoInteractions(orderRepository, feedbackRepository);
    }

    @Test
    void customerCannotAttachSomeoneElsesOrder() {
        when(userRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCodeAndUserId("BMK-OTHER", 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(5L, request(null, "BMK-OTHER"), IP))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy đơn hàng");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void ownOrderDecidesStoreAndClientStoreIsIgnored() {
        Order order = new Order();
        order.setOrderCode("BMK-MINE");
        order.setStore(storeB);
        when(userRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(customer));
        when(orderRepository.findByOrderCodeAndUserId("BMK-MINE", 5L)).thenReturn(Optional.of(order));
        when(siteSettingService.getPublicSettings()).thenReturn(Map.of("contactEmail", ""));

        assertThat(service.submit(5L, request(1L, " BMK-MINE "), IP)).isTrue();

        Feedback saved = captureSaved();
        assertThat(saved.getStore()).isSameAs(storeB);
        assertThat(saved.getRelatedOrder()).isSameAs(order);
        assertThat(saved.getUser()).isSameAs(customer);
        assertThat(saved.getClientIp()).isEqualTo(IP);
        assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.NEW);
        verifyNoInteractions(storeRepository);
    }

    @Test
    void requiredFieldsAndContactRules() {
        assertThatThrownBy(() -> service.submit(null, new FeedbackRequest(null, null, null, "An", "0901234567",
                null, "Tiêu đề", "Nội dung đủ dài rồi", null), IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Vui lòng chọn loại phản hồi");
        assertThatThrownBy(() -> service.submit(null, new FeedbackRequest(FeedbackType.OTHER, null, null, "An",
                " ", "", "Tiêu đề", "Nội dung đủ dài rồi", null), IP))
                .hasMessage("Vui lòng nhập số điện thoại hoặc email để cửa hàng liên hệ lại");
        assertThatThrownBy(() -> service.submit(null, new FeedbackRequest(FeedbackType.OTHER, null, null, "An",
                "0901234567", null, " ", "Nội dung đủ dài rồi", null), IP))
                .hasMessage("Vui lòng nhập tiêu đề");
        assertThatThrownBy(() -> service.submit(null, new FeedbackRequest(FeedbackType.OTHER, null, null, "An",
                "0901234567", null, "Tiêu đề", "Quá ngắn", null), IP))
                .hasMessage("Nội dung phải từ 10 đến 5000 ký tự");
        assertThatThrownBy(() -> service.submit(null, new FeedbackRequest(FeedbackType.OTHER, null, null, "An",
                "0901234567", null, "Tiêu đề", "x".repeat(5001), null), IP))
                .hasMessage("Nội dung phải từ 10 đến 5000 ký tự");
        verify(feedbackRepository, never()).save(any());
    }

    @Test
    void storeMustBeActive() {
        when(storeRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(store(9L, "CS-OFF", false)));

        assertThatThrownBy(() -> service.submit(null, request(9L, null), IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Cơ sở không hợp lệ hoặc đã ngừng hoạt động");
    }

    @Test
    void loggedInSenderDefaultsNameAndEmailFromAccount() {
        when(userRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(customer));
        when(siteSettingService.getPublicSettings()).thenReturn(Map.of());

        service.submit(5L, new FeedbackRequest(FeedbackType.SUGGESTION, null, null, " ", null, null,
                "Thêm món chay", "Mong quán có thêm bánh mì chay.", null), IP);

        Feedback saved = captureSaved();
        assertThat(saved.getFullName()).isEqualTo(customer.getFullName());
        assertThat(saved.getEmail()).isEqualTo("khach@banhmy.vn");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getStore()).isNull();
    }

    @Test
    void notifiesContactEmailAndEmailFailureDoesNotBreakSubmission() {
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(storeA));
        when(siteSettingService.getPublicSettings()).thenReturn(Map.of("contactEmail", " admin@banhmy.vn "));
        doThrow(new RuntimeException("SMTP down")).when(emailService)
                .sendFeedbackNotice(anyString(), any(FeedbackNotice.class));

        assertThat(service.submit(null, request(1L, null), IP)).isTrue();

        ArgumentCaptor<FeedbackNotice> notice = ArgumentCaptor.forClass(FeedbackNotice.class);
        verify(emailService).sendFeedbackNotice(eq("admin@banhmy.vn"), notice.capture());
        assertThat(notice.getValue().typeLabel()).isEqualTo("Khiếu nại");
        assertThat(notice.getValue().storeName()).isEqualTo("Cơ sở CS-A");
    }

    @Test
    void subjectNewlinesAreFlattened() {
        when(siteSettingService.getPublicSettings()).thenReturn(Map.of());

        service.submit(null, new FeedbackRequest(FeedbackType.OTHER, null, null, "An", "0901234567", null,
                "Dòng 1\r\nBcc: x@y.z", "Nội dung đủ dài rồi", null), IP);

        assertThat(captureSaved().getSubject()).isEqualTo("Dòng 1 Bcc: x@y.z");
    }

    // ------------------------------------------------------------------ xử lý phản hồi

    @Test
    void managerSeesOnlyOwnStoreAndNeverChainWideFeedback() {
        User manager = user(50L, RoleName.MANAGER, storeA);
        when(userRepository.findById(50L)).thenReturn(Optional.of(manager));
        when(feedbackRepository.search(isNull(), eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feedback(10L, storeA))));
        when(feedbackRepository.findById(11L)).thenReturn(Optional.of(feedback(11L, null)));
        when(feedbackRepository.findById(12L)).thenReturn(Optional.of(feedback(12L, storeB)));

        PageResponse<FeedbackResponse> page = service.search(50L, null, null, null, 0, 20);

        assertThat(page.content()).extracting(FeedbackResponse::storeId).containsExactly(1L);
        assertThatThrownBy(() -> service.get(50L, 11L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy phản hồi");
        assertThatThrownBy(() -> service.get(50L, 12L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.search(50L, null, 2L, null, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStatusRecordsHandler() {
        User admin = user(1L, RoleName.ADMIN, null);
        Feedback feedback = feedback(20L, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(feedbackRepository.findById(20L)).thenReturn(Optional.of(feedback));
        when(feedbackRepository.save(feedback)).thenReturn(feedback);

        FeedbackResponse updated = service.update(1L, 20L,
                new UpdateFeedbackRequest(FeedbackStatus.RESOLVED, "  Đã gọi xin lỗi khách "));

        assertThat(updated.status()).isEqualTo(FeedbackStatus.RESOLVED);
        assertThat(updated.resolutionNote()).isEqualTo("Đã gọi xin lỗi khách");
        assertThat(feedback.getHandledBy()).isSameAs(admin);
        assertThat(feedback.getHandledAt()).isEqualTo(NOW);
    }

    @Test
    void countNewUsesScopeAndStaffIsForbidden() {
        when(userRepository.findById(50L)).thenReturn(Optional.of(user(50L, RoleName.MANAGER, storeA)));
        when(userRepository.findById(60L)).thenReturn(Optional.of(user(60L, RoleName.STAFF, storeA)));
        when(feedbackRepository.countByStatusInScope(FeedbackStatus.NEW, 1L)).thenReturn(2L);

        assertThat(service.countNew(50L)).isEqualTo(2L);
        assertThatThrownBy(() -> service.countNew(60L)).isInstanceOf(BusinessException.class);
    }

    // ----------------------------------------------------------------------- helpers

    private Feedback captureSaved() {
        ArgumentCaptor<Feedback> captor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(captor.capture());
        return captor.getValue();
    }

    private static FeedbackRequest request(Long storeId, String orderCode) {
        return new FeedbackRequest(FeedbackType.COMPLAINT, storeId, orderCode, "Trần Thị Bình", "0912345678",
                null, "Bánh giao tới bị nguội", "Đơn giao chậm 20 phút, bánh nguội.", null);
    }

    private static Store store(Long id, String code, boolean active) {
        Store store = new Store();
        store.setId(id);
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ");
        store.setActive(active);
        return store;
    }

    private static User user(Long id, RoleName role, Store store) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setStore(store);
        user.setFullName("Người dùng " + id);
        return user;
    }

    private static Feedback feedback(Long id, Store store) {
        Feedback feedback = new Feedback();
        feedback.setId(id);
        feedback.setType(FeedbackType.SUGGESTION);
        feedback.setStore(store);
        feedback.setFullName("Khách");
        feedback.setPhone("0901234567");
        feedback.setSubject("Góp ý");
        feedback.setContent("Nội dung góp ý đủ dài");
        return feedback;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=FeedbackServiceTest`
Expected: COMPILATION ERROR (`FeedbackService`, các DTO `dto.feedback.*`, `EmailService.sendFeedbackNotice` chưa tồn tại).

- [ ] **Step 3: DTO trong `M/dto/feedback/`**

`FeedbackRequest.java`:

```java
package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.enums.FeedbackType;

/**
 * Gửi phản hồi (spec D §5). orderCode chỉ dùng được khi đăng nhập và là chủ đơn; khi có đơn,
 * cơ sở lấy theo đơn (storeId bị bỏ qua). website là ô bẫy bot.
 */
public record FeedbackRequest(
        FeedbackType type,
        Long storeId,
        String orderCode,
        String fullName,
        String phone,
        String email,
        String subject,
        String content,
        String website
) {
}
```

`UpdateFeedbackRequest.java`:

```java
package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.enums.FeedbackStatus;

/** PATCH phản hồi: trường null = giữ nguyên; resolutionNote rỗng = xoá ghi chú. */
public record UpdateFeedbackRequest(FeedbackStatus status, String resolutionNote) {
}
```

`FeedbackResponse.java`:

```java
package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import java.time.LocalDateTime;

/** Phản hồi cho màn quản lý. storeId null = chung toàn chuỗi. */
public record FeedbackResponse(
        Long id,
        FeedbackType type,
        Long storeId,
        String storeName,
        String orderCode,
        Long userId,
        String fullName,
        String phone,
        String email,
        String subject,
        String content,
        FeedbackStatus status,
        String resolutionNote,
        String handledByName,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {
    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(feedback.getId(), feedback.getType(),
                feedback.getStore() == null ? null : feedback.getStore().getId(),
                feedback.getStore() == null ? null : feedback.getStore().getName(),
                feedback.getRelatedOrder() == null ? null : feedback.getRelatedOrder().getOrderCode(),
                feedback.getUser() == null ? null : feedback.getUser().getId(),
                feedback.getFullName(), feedback.getPhone(), feedback.getEmail(), feedback.getSubject(),
                feedback.getContent(), feedback.getStatus(), feedback.getResolutionNote(),
                feedback.getHandledBy() == null ? null : feedback.getHandledBy().getFullName(),
                feedback.getHandledAt(), feedback.getCreatedAt());
    }
}
```

`FeedbackNotice.java`:

```java
package com.banhmyking.banhmyking.dto.feedback;

import com.banhmyking.banhmyking.entity.Feedback;

/** Dữ liệu email báo admin có phản hồi mới (EmailServiceImpl tự escape HTML). */
public record FeedbackNotice(
        String typeLabel,
        String subject,
        String senderName,
        String phone,
        String email,
        String storeName,
        String orderCode,
        String content
) {
    public static FeedbackNotice from(Feedback feedback) {
        return new FeedbackNotice(feedback.getType().getLabel(), feedback.getSubject(), feedback.getFullName(),
                feedback.getPhone(), feedback.getEmail(),
                feedback.getStore() == null ? null : feedback.getStore().getName(),
                feedback.getRelatedOrder() == null ? null : feedback.getRelatedOrder().getOrderCode(),
                feedback.getContent());
    }
}
```

- [ ] **Step 4: `EmailService` — thêm báo phản hồi mới**

Trong `M/service/EmailService.java`, thêm import `import com.banhmyking.banhmyking.dto.feedback.FeedbackNotice;` và phương thức (sau `sendApplicationConfirmation`):

```java
    /** Báo email cấu hình "contactEmail" có phản hồi mới (spec D §5). Lỗi SMTP chỉ được log. */
    void sendFeedbackNotice(String to, FeedbackNotice notice);
```

Trong `M/service/impl/EmailServiceImpl.java`, thêm import `import com.banhmyking.banhmyking.dto.feedback.FeedbackNotice;` và sau `sendApplicationConfirmation`:

```java
    @Override
    public void sendFeedbackNotice(String to, FeedbackNotice notice) {
        String rows = infoRow("Loại", notice.typeLabel())
                + infoRow("Người gửi", notice.senderName())
                + infoRow("Điện thoại", notice.phone())
                + infoRow("Email", notice.email())
                + infoRow("Cơ sở", notice.storeName() == null ? "Chung toàn chuỗi" : notice.storeName())
                + infoRow("Đơn hàng", notice.orderCode());
        String html = """
                <div style="font-family:Arial,'Helvetica Neue',sans-serif;max-width:560px;margin:0 auto;padding:24px;color:#2b1a0e">
                  <h2 style="color:#b45309;margin:0 0 12px">Phản hồi mới: %s</h2>
                  <table style="border-collapse:collapse;font-size:14px;margin-bottom:12px">%s</table>
                  <p style="white-space:pre-wrap;border-left:3px solid #f59e0b;padding-left:12px">%s</p>
                  <p><a href="%s" style="color:#b45309">Mở mục Phản hồi trong trang quản trị</a></p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(notice.subject()), rows, HtmlUtils.htmlEscape(notice.content()),
                frontendBaseUrl + "/admin/feedbacks");
        send(to, "[Phản hồi mới] " + notice.subject(), html, "báo phản hồi mới");
    }

    private static String infoRow(String label, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return "<tr><td style=\"padding:4px 12px 4px 0;color:#6b7280\">%s</td><td><strong>%s</strong></td></tr>"
                .formatted(label, HtmlUtils.htmlEscape(value));
    }
```

- [ ] **Step 5: `M/service/FeedbackService.java`**

```java
package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackNotice;
import com.banhmyking.banhmyking.dto.feedback.FeedbackRequest;
import com.banhmyking.banhmyking.dto.feedback.FeedbackResponse;
import com.banhmyking.banhmyking.dto.feedback.UpdateFeedbackRequest;
import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.FeedbackRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.security.SubmissionRateLimiter;
import com.banhmyking.banhmyking.util.ContactFields;
import com.banhmyking.banhmyking.util.PageableFactory;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phản hồi của khách (spec D §5). Gắn đơn → phải đăng nhập và là chủ đơn, cơ sở lấy theo đơn.
 * MANAGER chỉ thấy phản hồi của cơ sở mình; phản hồi chung toàn chuỗi (store NULL) chỉ ADMIN thấy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    static final String NOT_FOUND = "Không tìm thấy phản hồi";
    static final String ORDER_NOT_FOUND = "Không tìm thấy đơn hàng";
    private static final int CONTENT_MIN = 10;
    private static final int CONTENT_MAX = 5000;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final FeedbackRepository feedbackRepository;
    private final OrderRepository orderRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreAccessGuard storeAccessGuard;
    private final SubmissionRateLimiter rateLimiter;
    private final SiteSettingService siteSettingService;
    private final EmailService emailService;
    private final Clock clock;

    // ------------------------------------------------------------------ gửi phản hồi (công khai)

    /** @param senderId null khi chưa đăng nhập. @return false khi dính ô bẫy bot (không lưu). */
    @Transactional
    public boolean submit(Long senderId, FeedbackRequest request, String clientIp) {
        if (ContactFields.trimToNull(request.website()) != null) {
            log.info("Bỏ qua phản hồi dính ô bẫy bot từ IP {}", clientIp);
            return false;
        }
        rateLimiter.check(clientIp);

        User sender = senderId == null ? null : userRepository.findByIdAndDeletedFalse(senderId).orElse(null);
        if (request.type() == null) {
            throw invalid("Vui lòng chọn loại phản hồi");
        }
        String fullName = ContactFields.requireText(
                firstNonBlank(request.fullName(), sender == null ? null : sender.getFullName()), 100,
                "Vui lòng nhập họ tên", "Họ tên tối đa 100 ký tự");
        String phone = ContactFields.phone(request.phone());
        String email = ContactFields.email(firstNonBlank(request.email(), sender == null ? null : sender.getEmail()));
        if (phone == null && email == null) {
            throw invalid("Vui lòng nhập số điện thoại hoặc email để cửa hàng liên hệ lại");
        }
        String subject = ContactFields.requireText(request.subject(), 200, "Vui lòng nhập tiêu đề",
                "Tiêu đề tối đa 200 ký tự").replaceAll("[\\r\\n]+", " ");
        String content = ContactFields.trimToNull(request.content());
        if (content == null || content.length() < CONTENT_MIN || content.length() > CONTENT_MAX) {
            throw invalid("Nội dung phải từ 10 đến 5000 ký tự");
        }

        Order order = null;
        Store store = null;
        String orderCode = ContactFields.trimToNull(request.orderCode());
        if (orderCode != null) {
            if (sender == null) {
                throw new ResourceNotFoundException(ORDER_NOT_FOUND);
            }
            order = orderRepository.findByOrderCodeAndUserId(orderCode, sender.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND));
            store = order.getStore();
        } else if (request.storeId() != null) {
            store = storeRepository.findByIdAndDeletedFalse(request.storeId())
                    .filter(Store::isActive)
                    .orElseThrow(() -> invalid("Cơ sở không hợp lệ hoặc đã ngừng hoạt động"));
        }

        Feedback feedback = new Feedback();
        feedback.setType(request.type());
        feedback.setStore(store);
        feedback.setRelatedOrder(order);
        feedback.setUser(sender);
        feedback.setFullName(fullName);
        feedback.setPhone(phone);
        feedback.setEmail(email);
        feedback.setSubject(subject);
        feedback.setContent(content);
        feedback.setClientIp(clientIp);
        feedbackRepository.save(feedback);
        rateLimiter.record(clientIp);

        notifyContactEmail(feedback);
        return true;
    }

    // ------------------------------------------------------------------ xử lý (MANAGER / ADMIN)

    @Transactional(readOnly = true)
    public PageResponse<FeedbackResponse> search(Long actorId, FeedbackType type, Long storeId,
                                                 FeedbackStatus status, int page, int size) {
        User actor = requireInboxActor(actorId);
        Long scopedStoreId = storeAccessGuard.resolveStoreFilter(actor, storeId);
        return PageResponse.from(feedbackRepository.search(type, scopedStoreId, status,
                        PageableFactory.of(page, size, NEWEST_FIRST))
                .map(FeedbackResponse::from));
    }

    @Transactional(readOnly = true)
    public FeedbackResponse get(Long actorId, Long id) {
        return FeedbackResponse.from(requireInScope(requireInboxActor(actorId), id));
    }

    @Transactional
    public FeedbackResponse update(Long actorId, Long id, UpdateFeedbackRequest request) {
        User actor = requireInboxActor(actorId);
        Feedback feedback = requireInScope(actor, id);
        if (request.status() != null && request.status() != feedback.getStatus()) {
            feedback.setStatus(request.status());
            if (request.status() != FeedbackStatus.NEW) {
                feedback.setHandledBy(actor);
                feedback.setHandledAt(LocalDateTime.now(clock));
            }
        }
        if (request.resolutionNote() != null) {
            feedback.setResolutionNote(ContactFields.optionalText(request.resolutionNote(), 2000,
                    "Ghi chú xử lý tối đa 2000 ký tự"));
        }
        return FeedbackResponse.from(feedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public long countNew(Long actorId) {
        User actor = requireInboxActor(actorId);
        return feedbackRepository.countByStatusInScope(FeedbackStatus.NEW, storeAccessGuard.scopedStoreId(actor));
    }

    // ------------------------------------------------------------------ nội bộ

    /** Lỗi gửi email (kể cả lỗi đọc cấu hình) chỉ ghi log, không ảnh hưởng kết quả gửi phản hồi. */
    private void notifyContactEmail(Feedback feedback) {
        try {
            String to = ContactFields.trimToNull(siteSettingService.getPublicSettings().get(SiteSettingKeys.CONTACT_EMAIL));
            if (to != null) {
                emailService.sendFeedbackNotice(to, FeedbackNotice.from(feedback));
            }
        } catch (RuntimeException ex) {
            log.error("Không gửi được email báo phản hồi mới: {}", ex.getMessage());
        }
    }

    private User requireInboxActor(Long actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(actorId)));
        if (actor.getRole() != RoleName.MANAGER && actor.getRole() != RoleName.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Chỉ quản lý cơ sở hoặc quản trị viên mới xem được phản hồi");
        }
        return actor;
    }

    /** MANAGER: phản hồi cơ sở khác hoặc chung toàn chuỗi → 404. */
    private Feedback requireInScope(User actor, Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        Long ownStoreId = storeAccessGuard.scopedStoreId(actor);
        if (ownStoreId != null && (feedback.getStore() == null || !ownStoreId.equals(feedback.getStore().getId()))) {
            throw new ResourceNotFoundException(NOT_FOUND);
        }
        return feedback;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return ContactFields.trimToNull(preferred) != null ? preferred : fallback;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
```

- [ ] **Step 6: Chạy test service, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=FeedbackServiceTest`
Expected: `Tests run: 12, Failures: 0, Errors: 0`.

- [ ] **Step 7: Viết test controller (đỏ)**

`T/controller/FeedbackControllerTest.java`:

```java
package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackRequest;
import com.banhmyking.banhmyking.dto.feedback.FeedbackResponse;
import com.banhmyking.banhmyking.dto.feedback.UpdateFeedbackRequest;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.service.FeedbackService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    private static final String BODY = """
            {"type":"COMPLAINT","orderCode":"BMK-1","fullName":"Bình","phone":"0912345678",
             "subject":"Bánh nguội","content":"Đơn giao chậm, bánh nguội."}
            """;

    @Mock private FeedbackService feedbackService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FeedbackController(feedbackService, new ClientIpResolver(false)),
                        new AdminFeedbackController(feedbackService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousSubmissionPassesNullSenderAndClientIp() throws Exception {
        when(feedbackService.submit(isNull(), any(FeedbackRequest.class), eq("10.2.3.4"))).thenReturn(true);

        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(request -> {
                            request.setRemoteAddr("10.2.3.4");
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cảm ơn bạn! Phản hồi đã được gửi tới cửa hàng."))
                .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<FeedbackRequest> captor = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(feedbackService).submit(isNull(), captor.capture(), eq("10.2.3.4"));
        assertThat(captor.getValue().type()).isEqualTo(FeedbackType.COMPLAINT);
        assertThat(captor.getValue().orderCode()).isEqualTo("BMK-1");
    }

    @Test
    void loggedInSubmissionPassesUserIdAndErrorsMap() throws Exception {
        login("5", "ROLE_CUSTOMER");
        when(feedbackService.submit(eq(5L), any(FeedbackRequest.class), any()))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy đơn hàng"))
                .thenThrow(new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau"));

        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));
        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void inboxEndpointsPassActorId() throws Exception {
        login("50", "ROLE_MANAGER");
        when(feedbackService.search(50L, FeedbackType.COMPLAINT, null, FeedbackStatus.NEW, 0, 20))
                .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1, true));
        when(feedbackService.countNew(50L)).thenReturn(2L);
        when(feedbackService.get(50L, 3L)).thenReturn(response());
        when(feedbackService.update(eq(50L), eq(3L), any(UpdateFeedbackRequest.class))).thenReturn(response());

        mockMvc.perform(get("/api/v1/admin/feedbacks").param("type", "COMPLAINT").param("status", "NEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].orderCode").value("BMK-1"));
        mockMvc.perform(get("/api/v1/admin/feedbacks/count-new")).andExpect(jsonPath("$.data").value(2));
        mockMvc.perform(get("/api/v1/admin/feedbacks/3")).andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/feedbacks/3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_PROGRESS\",\"resolutionNote\":\"Đang gọi khách\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã cập nhật phản hồi"));

        ArgumentCaptor<UpdateFeedbackRequest> captor = ArgumentCaptor.forClass(UpdateFeedbackRequest.class);
        verify(feedbackService).update(eq(50L), eq(3L), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(FeedbackStatus.IN_PROGRESS);
    }

    private static void login(String userId, String authority) {
        UserDetails principal = User.withUsername(userId).password("x").authorities(authority).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static FeedbackResponse response() {
        return new FeedbackResponse(3L, FeedbackType.COMPLAINT, 1L, "Cơ sở 1", "BMK-1", 5L, "Bình", "0912345678",
                null, "Bánh nguội", "Đơn giao chậm, bánh nguội.", FeedbackStatus.NEW, null, null, null,
                LocalDateTime.of(2026, 10, 2, 9, 0));
    }
}
```

- [ ] **Step 8: Chạy test controller, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=FeedbackControllerTest`
Expected: COMPILATION ERROR (`FeedbackController`, `AdminFeedbackController` chưa tồn tại).

- [ ] **Step 9: `M/controller/FeedbackController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackRequest;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gửi phản hồi — công khai, đăng nhập tuỳ chọn (spec D §5). */
@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedbacks", description = "Gửi phản hồi công khai")
public class FeedbackController {

    public static final String SUBMITTED = "Cảm ơn bạn! Phản hồi đã được gửi tới cửa hàng.";

    private final FeedbackService feedbackService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @Operation(summary = "Gửi phản hồi",
            description = "orderCode chỉ dùng khi đăng nhập và là chủ đơn (không thì 404). Tối đa 5 lần/giờ/IP → 429.")
    public ResponseEntity<ApiResponse<Void>> submit(@RequestBody FeedbackRequest request,
                                                    @AuthenticationPrincipal UserDetails principal,
                                                    HttpServletRequest httpRequest) {
        Long senderId = principal == null ? null : SecurityUtils.requireUserId(principal);
        feedbackService.submit(senderId, request, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(SUBMITTED));
    }
}
```

- [ ] **Step 10: `M/controller/AdminFeedbackController.java`**

```java
package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackResponse;
import com.banhmyking.banhmyking.dto.feedback.UpdateFeedbackRequest;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Xử lý phản hồi: MANAGER cơ sở mình, ADMIN tất cả (kể cả chung toàn chuỗi). */
@RestController
@RequestMapping("/api/v1/admin/feedbacks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@Tag(name = "Admin feedbacks", description = "Hộp phản hồi theo phạm vi cơ sở")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    @Operation(summary = "Danh sách phản hồi", description = "MANAGER bị ép storeId = cơ sở mình; cơ sở khác → 404.")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> list(
            @RequestParam(required = false) FeedbackType type,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phản hồi thành công", feedbackService.search(
                SecurityUtils.requireUserId(principal), type, storeId, status, page, size)));
    }

    @GetMapping("/count-new")
    public ResponseEntity<ApiResponse<Long>> countNew(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.countNew(SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> get(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy phản hồi thành công",
                feedbackService.get(SecurityUtils.requireUserId(principal), id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> update(@PathVariable Long id,
                                                                @RequestBody UpdateFeedbackRequest request,
                                                                @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật phản hồi",
                feedbackService.update(SecurityUtils.requireUserId(principal), id, request)));
    }
}
```

- [ ] **Step 11: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest='FeedbackServiceTest,FeedbackControllerTest,JobApplicationServiceTest'`
Expected: `Tests run: 28, Failures: 0, Errors: 0` (12 + 3 + 13) → BUILD SUCCESS.

- [ ] **Step 12: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/dto/feedback src/main/java/com/banhmyking/banhmyking/service/FeedbackService.java src/main/java/com/banhmyking/banhmyking/service/EmailService.java src/main/java/com/banhmyking/banhmyking/service/impl/EmailServiceImpl.java src/main/java/com/banhmyking/banhmyking/controller/FeedbackController.java src/main/java/com/banhmyking/banhmyking/controller/AdminFeedbackController.java src/test/java/com/banhmyking/banhmyking/service/FeedbackServiceTest.java src/test/java/com/banhmyking/banhmyking/controller/FeedbackControllerTest.java
git commit -m "feat(feedback): gửi phản hồi gắn cơ sở/đơn của chính mình, email báo admin, hộp phản hồi theo cơ sở"
```

---

## Task 9: Phân quyền URL + test bảo mật đầu-cuối (vai trò, phạm vi cơ sở, CV, chống spam)

**Files:**
- Modify: `M/config/SecurityConfig.java` (thêm matcher cho API mới)
- Test: `T/controller/NewsCareersSecurityTest.java`

**Interfaces:**
- Consumes: toàn bộ API Task 3–8; `JwtTokenProvider.generateAccessToken(User)`; repository Task 1; `EmailService` (thay bằng `@MockitoBean` để test không gọi SMTP).
- Produces: quy tắc truy cập (spec D §6):
  - Công khai: `GET /api/v1/news/**`, `GET /api/v1/jobs/**`, `POST /api/v1/jobs/*/applications`, `POST /api/v1/feedbacks` (có token hợp lệ thì vẫn nhận người gửi).
  - `MANAGER`, `ADMIN`: `/api/v1/job-applications/**`, `/api/v1/admin/feedbacks/**` (đặt **trước** `/api/v1/admin/**`).
  - Chỉ `ADMIN`: `/api/v1/admin/news/**`, `/api/v1/admin/jobs/**` (rơi vào rule `/api/v1/admin/**` có sẵn).
  - `/private-uploads/**` không có rule → `anyRequest().denyAll()`; không có resource handler nào cho thư mục này.

- [ ] **Step 1: Viết test bảo mật (đỏ)**

`T/controller/NewsCareersSecurityTest.java`:

```java
package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.FeedbackRepository;
import com.banhmyking.banhmyking.repository.JobApplicationRepository;
import com.banhmyking.banhmyking.repository.JobPostingRepository;
import com.banhmyking.banhmyking.repository.NewsPostRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import com.banhmyking.banhmyking.service.EmailService;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Phân quyền đầu-cuối (filter chain + JWT thật + service) cho tin tức / tuyển dụng / phản hồi (spec D §6, §7).
 * DB dev nhưng mọi dữ liệu do test tạo trong transaction và rollback; CV ghi vào target/test-private-uploads
 * và được xoá trong test. EmailService là mock để không gọi SMTP thật.
 */
@SpringBootTest(properties = "app.private-upload-dir=target/test-private-uploads")
@Transactional
class NewsCareersSecurityTest {

    private static final AtomicInteger IP_SEQ = new AtomicInteger();
    private static final byte[] PDF = "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    @Autowired private WebApplicationContext context;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NewsPostRepository newsPostRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private JobApplicationRepository jobApplicationRepository;
    @Autowired private FeedbackRepository feedbackRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private EmailService emailService;

    private MockMvc mockMvc;
    private Store storeA;
    private Store storeB;
    private User managerA;
    private User staffA;
    private User customer;
    private User otherCustomer;
    private User admin;
    private NewsPost published;
    private NewsPost draft;
    private NewsPost scheduled;
    private JobPosting job;
    private JobApplication appA;
    private JobApplication appB;
    private Feedback feedbackA;
    private Feedback feedbackB;
    private Feedback feedbackChainWide;
    private Order customerOrder;
    private Order otherOrder;

    @BeforeEach
    void setUp() {
        Filter securityChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();

        String tag = Long.toString(System.nanoTime(), 36);
        storeA = storeRepository.save(store("NA" + tag));
        storeB = storeRepository.save(store("NB" + tag));
        managerA = userRepository.save(user(tag, "manager", RoleName.MANAGER, storeA));
        staffA = userRepository.save(user(tag, "staff", RoleName.STAFF, storeA));
        customer = userRepository.save(user(tag, "customer", RoleName.CUSTOMER, null));
        otherCustomer = userRepository.save(user(tag, "other", RoleName.CUSTOMER, null));
        admin = userRepository.save(user(tag, "admin", RoleName.ADMIN, null));

        published = newsPostRepository.save(news("pub-" + tag, NewsStatus.PUBLISHED, LocalDateTime.of(2020, 1, 1, 8, 0)));
        draft = newsPostRepository.save(news("draft-" + tag, NewsStatus.DRAFT, null));
        scheduled = newsPostRepository.save(news("sch-" + tag, NewsStatus.PUBLISHED, LocalDateTime.of(2999, 1, 1, 0, 0)));

        JobPosting posting = new JobPosting();
        posting.setTitle("Phụ bếp " + tag);
        posting.setSlug("job-" + tag);
        posting.setEmploymentType(EmploymentType.PART_TIME);
        posting.setDescription("## Mô tả");
        posting.setStatus(JobStatus.OPEN);
        posting.getStores().addAll(List.of(storeA, storeB));
        job = jobPostingRepository.save(posting);
        appA = jobApplicationRepository.save(application(storeA, "0901111111"));
        appB = jobApplicationRepository.save(application(storeB, "0902222222"));

        customerOrder = orderRepository.save(order("SC-" + tag, customer, storeB));
        otherOrder = orderRepository.save(order("SO-" + tag, otherCustomer, storeB));
        feedbackA = feedbackRepository.save(feedback(storeA));
        feedbackB = feedbackRepository.save(feedback(storeB));
        feedbackChainWide = feedbackRepository.save(feedback(null));
        storeRepository.flush();
    }

    // ------------------------------------------------------------------ công khai

    @Test
    void anonymousReadsPublishedContentButNotDraftOrScheduledNews() throws Exception {
        mockMvc.perform(get("/api/v1/news")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/news/latest")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/news/" + published.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(published.getId()));
        mockMvc.perform(get("/api/v1/news/" + draft.getSlug())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/news/" + scheduled.getSlug())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/jobs")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/jobs/" + job.getSlug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stores.length()").value(2))
                .andExpect(jsonPath("$.data.acceptingApplications").value(true));
    }

    // ------------------------------------------------------------------ vai trò

    @Test
    void anonymousGets401AndWrongRolesGet403OnManagementApis() throws Exception {
        for (String url : List.of("/api/v1/admin/news", "/api/v1/admin/jobs", "/api/v1/job-applications",
                "/api/v1/admin/feedbacks")) {
            mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
            mockMvc.perform(as(customer, get(url))).andExpect(status().isForbidden());
            mockMvc.perform(as(staffA, get(url))).andExpect(status().isForbidden());
        }
        mockMvc.perform(as(managerA, get("/api/v1/admin/news"))).andExpect(status().isForbidden());
        mockMvc.perform(as(managerA, get("/api/v1/admin/jobs"))).andExpect(status().isForbidden());
        mockMvc.perform(as(admin, get("/api/v1/admin/news"))).andExpect(status().isOk());
        mockMvc.perform(as(admin, get("/api/v1/admin/jobs"))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ phạm vi cơ sở

    @Test
    void managerSeesOnlyOwnStoreApplicationsAndFeedbacks() throws Exception {
        mockMvc.perform(as(managerA, get("/api/v1/job-applications")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(appA.getId()));
        mockMvc.perform(as(managerA, get("/api/v1/job-applications").param("storeId", storeB.getId().toString())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(managerA, get("/api/v1/job-applications/" + appB.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mockMvc.perform(as(managerA, get("/api/v1/job-applications/" + appB.getId() + "/cv")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(managerA, patch("/api/v1/job-applications/" + appB.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIRED\"}")))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(managerA, get("/api/v1/job-applications/count-new")))
                .andExpect(jsonPath("$.data").value(1));

        mockMvc.perform(as(managerA, get("/api/v1/admin/feedbacks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(feedbackA.getId()));
        mockMvc.perform(as(managerA, get("/api/v1/admin/feedbacks/" + feedbackB.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(managerA, get("/api/v1/admin/feedbacks/" + feedbackChainWide.getId())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(managerA, patch("/api/v1/admin/feedbacks/" + feedbackB.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RESOLVED\"}")))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(admin, get("/api/v1/admin/feedbacks/" + feedbackChainWide.getId())))
                .andExpect(status().isOk());
        mockMvc.perform(as(admin, get("/api/v1/job-applications/" + appB.getId()))).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ CV

    @Test
    void cvIsDownloadableOnlyThroughCheckedApiNeverUnderUploads() throws Exception {
        mockMvc.perform(apply("0903333333", storeA.getId(),
                        new MockMultipartFile("cv", "CV Ứng viên.pdf", "application/pdf", PDF), nextIp()))
                .andExpect(status().isOk());
        JobApplication saved = jobApplicationRepository.search(job.getId(), storeA.getId(), null, PageRequest.of(0, 20))
                .getContent().stream().filter(a -> "0903333333".equals(a.getPhone())).findFirst().orElseThrow();
        String key = saved.getCvFileKey();
        assertThat(key).matches("[a-f0-9]{32}\\.pdf");
        try {
            mockMvc.perform(as(managerA, get("/api/v1/job-applications/" + saved.getId() + "/cv")))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(content().bytes(PDF));
            mockMvc.perform(as(customer, get("/api/v1/job-applications/" + saved.getId() + "/cv")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/uploads/cv/" + key)).andExpect(status().isNotFound());
            mockMvc.perform(get("/uploads/" + key)).andExpect(status().isNotFound());
            mockMvc.perform(get("/private-uploads/cv/" + key)).andExpect(status().is4xxClientError());
        } finally {
            Files.deleteIfExists(Paths.get("target/test-private-uploads", "cv", key));
        }
    }

    @Test
    void renamedExecutableCvIsRejected() throws Exception {
        mockMvc.perform(apply("0905555555", storeA.getId(),
                        new MockMultipartFile("cv", "cv.pdf", "application/pdf", EXE), nextIp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File CV phải là PDF, JPG hoặc PNG"));
    }

    // ------------------------------------------------------------------ chống spam

    @Test
    void honeypotDuplicateAndSharedRateLimit() throws Exception {
        long before = jobApplicationRepository.countByStatusInScope(
                ApplicationStatus.NEW, storeA.getId());
        MockMultipartHttpServletRequestBuilder bot = multipart("/api/v1/jobs/" + job.getSlug() + "/applications");
        bot.param("storeId", storeA.getId().toString()).param("fullName", "Bot").param("phone", "0906666666")
                .param("website", "http://spam.example").with(fromIp(nextIp()));
        mockMvc.perform(bot).andExpect(status().isOk());
        assertThat(jobApplicationRepository.countByStatusInScope(
                ApplicationStatus.NEW, storeA.getId())).isEqualTo(before);

        String dupIp = nextIp();
        mockMvc.perform(apply("0907777777", storeA.getId(), null, dupIp)).andExpect(status().isOk());
        mockMvc.perform(apply("0907777777", storeB.getId(), null, dupIp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Bạn đã nộp hồ sơ cho vị trí này"));

        String spamIp = nextIp();
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON)
                            .content(feedbackJson(storeA.getId(), null)).with(fromIp(spamIp)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(storeA.getId(), null)).with(fromIp(spamIp)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("TOO_MANY_REQUESTS"));
        mockMvc.perform(apply("0908888888", storeA.getId(), null, spamIp))
                .andExpect(status().isTooManyRequests());
    }

    // ------------------------------------------------------------------ phản hồi gắn đơn

    @Test
    void feedbackOrderMustBelongToLoggedInSenderAndDecidesStore() throws Exception {
        mockMvc.perform(as(customer, post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(null, otherOrder.getOrderCode())).with(fromIp(nextIp()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Không tìm thấy đơn hàng"));
        mockMvc.perform(post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(null, customerOrder.getOrderCode())).with(fromIp(nextIp())))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(customer, post("/api/v1/feedbacks").contentType(MediaType.APPLICATION_JSON)
                        .content(feedbackJson(storeA.getId(), customerOrder.getOrderCode())).with(fromIp(nextIp()))))
                .andExpect(status().isOk());

        List<Feedback> inB = feedbackRepository.search(null, storeB.getId(), null, PageRequest.of(0, 20)).getContent();
        assertThat(inB).anySatisfy(f -> {
            assertThat(f.getRelatedOrder()).isNotNull();
            assertThat(f.getRelatedOrder().getOrderCode()).isEqualTo(customerOrder.getOrderCode());
            assertThat(f.getUser().getId()).isEqualTo(customer.getId());
        });
    }

    // ----------------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user));
    }

    private RequestBuilder apply(String phone, Long storeId, MockMultipartFile cv, String ip) {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/jobs/" + job.getSlug() + "/applications");
        if (cv != null) {
            request.file(cv);
        }
        request.param("storeId", storeId.toString()).param("fullName", "Ứng viên thử").param("phone", phone)
                .with(fromIp(ip));
        return request;
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String nextIp() {
        return "10.77.0." + IP_SEQ.incrementAndGet();
    }

    private static String feedbackJson(Long storeId, String orderCode) {
        return """
                {"type":"COMPLAINT","storeId":%s,"orderCode":%s,"fullName":"Khách thử","phone":"0912345678",
                 "subject":"Bánh nguội","content":"Bánh giao tới bị nguội, mong cửa hàng kiểm tra."}
                """.formatted(storeId == null ? "null" : storeId.toString(),
                orderCode == null ? "null" : "\"" + orderCode + "\"");
    }

    private static Store store(String code) {
        Store store = new Store();
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ thử nghiệm");
        return store;
    }

    private static User user(String tag, String kind, RoleName role, Store store) {
        User user = new User();
        user.setEmail(kind + "-" + tag + "@test.local");
        user.setPassword("not-used");
        user.setFullName("Sec " + kind);
        user.setRole(role);
        user.setStore(store);
        return user;
    }

    private static NewsPost news(String slug, NewsStatus status, LocalDateTime publishedAt) {
        NewsPost post = new NewsPost();
        post.setTitle("Tin " + slug);
        post.setSlug(slug);
        post.setContent("Nội dung");
        post.setStatus(status);
        post.setPublishedAt(publishedAt);
        return post;
    }

    private JobApplication application(Store store, String phone) {
        JobApplication app = new JobApplication();
        app.setJobPosting(job);
        app.setStore(store);
        app.setFullName("Ứng viên " + phone);
        app.setPhone(phone);
        return app;
    }

    private static Order order(String code, User owner, Store store) {
        Order order = new Order();
        order.setOrderCode(code);
        order.setUser(owner);
        order.setStore(store);
        order.setStatus(OrderStatus.DELIVERED);
        order.setReceiverName("Khách thử");
        order.setReceiverPhone("0900000000");
        order.setShippingAddress("1 Đường Thử");
        order.setSubtotal(new BigDecimal("45000"));
        order.setTotal(new BigDecimal("45000"));
        return order;
    }

    private static Feedback feedback(Store store) {
        Feedback feedback = new Feedback();
        feedback.setType(FeedbackType.SUGGESTION);
        feedback.setStore(store);
        feedback.setFullName("Khách góp ý");
        feedback.setPhone("0987654321");
        feedback.setSubject("Góp ý");
        feedback.setContent("Nội dung góp ý đủ dài.");
        return feedback;
    }
}
```

- [ ] **Step 2: Chạy test, xác nhận đỏ**

Run: `./mvnw -B clean test -Dtest=NewsCareersSecurityTest`
Expected: FAIL — các request công khai (`/api/v1/news`, `/api/v1/jobs`, nộp hồ sơ, gửi phản hồi) bị 401 vì rơi vào rule `/api/v1/**` authenticated; MANAGER bị 403 ở `/api/v1/admin/feedbacks` vì rule `/api/v1/admin/**` chỉ cho ADMIN.

- [ ] **Step 3: `SecurityConfig` — thêm matcher**

Trong `M/config/SecurityConfig.java`, sửa (Edit) đoạn:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores").permitAll()
                        .requestMatchers("/api/v1/manager/**").hasAnyRole("MANAGER", "ADMIN")
```

thành:

```java
                        .requestMatchers(HttpMethod.GET, "/api/v1/stores").permitAll()
                        // Tin tức / tuyển dụng / phản hồi (spec news-careers-feedback §6). Form công khai vẫn
                        // nhận token nếu có (gắn đơn của chính mình); admin/feedbacks phải đứng trước /admin/**.
                        .requestMatchers(HttpMethod.GET, "/api/v1/news", "/api/v1/news/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/jobs", "/api/v1/jobs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/jobs/*/applications").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/feedbacks").permitAll()
                        .requestMatchers("/api/v1/job-applications/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/admin/feedbacks/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/v1/manager/**").hasAnyRole("MANAGER", "ADMIN")
```

Không đụng `WebMvcConfig`: chỉ `uploads/` được map ra `/uploads/**`, `private-uploads/` không có handler.

- [ ] **Step 4: Chạy test, xác nhận xanh**

Run: `./mvnw -B clean test -Dtest=NewsCareersSecurityTest`
Expected: `Tests run: 7, Failures: 0, Errors: 0`. Sau test, thư mục `target/test-private-uploads/cv/` không còn file CV của test.

- [ ] **Step 5: Chạy toàn bộ test backend**

Run: `./mvnw -B clean test`
Expected: BUILD SUCCESS (mọi test cũ + mới xanh, gồm `StoreScopedEndpointsSecurityTest` không đổi hành vi).

- [ ] **Step 6: Commit (người dùng tự chạy)**

```
git add src/main/java/com/banhmyking/banhmyking/config/SecurityConfig.java src/test/java/com/banhmyking/banhmyking/controller/NewsCareersSecurityTest.java
git commit -m "feat(security): mở công khai tin tức/tuyển dụng/form, hồ sơ và phản hồi cho MANAGER/ADMIN; test phân quyền đầu-cuối"
```

---

## Task 10: Frontend — thư viện Markdown, types, API client, `MarkdownView`/`MarkdownEditor`, CSS dùng chung

**Files:**
- Modify: `frontend/package.json`, `frontend/package-lock.json` (qua `npm install`)
- Create (gốc `frontend/src/`): `types/content.ts`, `utils/contentLabels.ts`, `utils/inboxEvents.ts`, `api/newsApi.ts`, `api/jobApi.ts`, `api/feedbackApi.ts`, `components/content/MarkdownView.tsx`, `components/content/MarkdownEditor.tsx`, `styles/components/content.css`

**Interfaces:**
- Consumes: `axiosClient` (`api/axiosClient.ts`), `ApiResponse` (`types/auth`), `PageResponse` (`types/admin`), `formatDate` (`utils/formatters`), UI `Button`, `Tabs`, `useToast`, type `BadgeTone` (`components/ui`); API backend Task 3–8.
- Produces:
  - Types (`types/content.ts`): `NewsStatus`, `NewsDisplayState`, `NewsSummary`, `NewsDetail`, `AdminNews`, `NewsPayload`, `AdminNewsFilter`, `EmploymentType`, `JobStatus`, `StoreRef`, `JobSummary`, `JobDetail`, `AdminJob`, `JobPayload`, `AdminJobFilter`, `ApplicationStatus`, `JobApplication`, `ApplicationForm`, `ApplicationFilter`, `ApplicationUpdate`, `FeedbackType`, `FeedbackStatus`, `FeedbackPayload`, `Feedback`, `FeedbackFilter`, `FeedbackUpdate`.
  - `newsApi`: `listPublished(page?, size?)`, `latest(limit?)`, `getBySlug(slug)`, `adminList(filter)`, `adminGet(id)`, `adminCreate(payload)`, `adminUpdate(id, payload)`, `adminDelete(id)`.
  - `jobApi`: `listOpen(storeId?)`, `getBySlug(slug)`, `apply(slug, form, cv)` → message, `adminList(filter)`, `adminGet(id)`, `adminCreate(payload)`, `adminUpdate(id, payload)`, `adminSetStatus(id, status)`, `adminDelete(id)`, `listApplications(filter)`, `getApplication(id)`, `updateApplication(id, update)`, `downloadCv(application)`, `countNewApplications()`.
  - `feedbackApi`: `submit(payload)` → message, `adminList(filter)`, `adminGet(id)`, `adminUpdate(id, update)`, `countNew()`.
  - `utils/contentLabels.ts`: `NEWS_STATE_LABEL`, `NEWS_STATE_TONE`, `EMPLOYMENT_TYPE_LABEL`, `JOB_STATUS_LABEL`, `APPLICATION_STATUS_LABEL`, `APPLICATION_STATUS_TONE`, `FEEDBACK_TYPE_LABEL`, `FEEDBACK_STATUS_LABEL`, `FEEDBACK_STATUS_TONE`, `storesText(chainWide, stores)`, `deadlineText(deadline)`, `VN_PHONE_REGEX`, `EMAIL_REGEX`, `normalizePhone(raw)`.
  - `utils/inboxEvents.ts`: `INBOX_CHANGED_EVENT = 'bmk:inbox-changed'`, `notifyInboxChanged()`.
  - `<MarkdownView source={string} />` (react-markdown + rehype-sanitize, không HTML thô, link ngoài mở tab mới `rel="noopener noreferrer"`).
  - `<MarkdownEditor label value onChange onUploadImage? rows? placeholder? />` (thanh công cụ B, I, H2, H3, danh sách, link, chèn ảnh + tab Soạn/Xem trước).
  - CSS: `content.css` (khối `.md-*`, `.news-*`, `.job-*`, `.cf-form*`, `.hp-field`, `.inbox*`, `.order-peek`).

- [ ] **Step 1: Cài 2 thư viện (bản cố định)**

Run (trong `frontend/`): `npm install --save-exact react-markdown@10.1.0 rehype-sanitize@6.0.0`
Expected: `package.json` có `"react-markdown": "10.1.0"`, `"rehype-sanitize": "6.0.0"` trong `dependencies`; không có cảnh báo peer dependency với React 19.

- [ ] **Step 2: `frontend/src/types/content.ts`**

```ts
/** Khớp DTO backend dto/news, dto/job, dto/feedback (dự án con D). Thời điểm là giờ Việt Nam, không có múi giờ. */

export type NewsStatus = 'DRAFT' | 'PUBLISHED';
/** Suy ra từ status + publishedAt: PUBLISHED có publishedAt ở tương lai = SCHEDULED */
export type NewsDisplayState = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED';

export interface NewsSummary {
  id: number;
  title: string;
  slug: string;
  coverImageUrl?: string | null;
  summary?: string | null;
  publishedAt: string;
  pinned: boolean;
}

export interface NewsDetail {
  id: number;
  title: string;
  slug: string;
  coverImageUrl?: string | null;
  summary?: string | null;
  /** Markdown */
  content: string;
  publishedAt: string;
  related: NewsSummary[];
}

export interface AdminNews {
  id: number;
  title: string;
  slug: string;
  coverImageUrl?: string | null;
  summary?: string | null;
  /** null trong danh sách — gọi newsApi.adminGet để lấy nội dung */
  content?: string | null;
  status: NewsStatus;
  displayState: NewsDisplayState;
  publishedAt?: string | null;
  pinned: boolean;
  authorName?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface NewsPayload {
  title: string;
  /** Bỏ trống: tạo → sinh từ tiêu đề; sửa → giữ slug cũ */
  slug?: string | null;
  coverImageUrl?: string | null;
  summary?: string | null;
  content: string;
  status: NewsStatus;
  /** Giá trị ô datetime-local (yyyy-MM-ddTHH:mm, giờ Việt Nam); null + PUBLISHED = đăng ngay */
  publishedAt?: string | null;
  pinned: boolean;
}

export interface AdminNewsFilter {
  status?: NewsDisplayState;
  keyword?: string;
  page?: number;
  size?: number;
}

export type EmploymentType = 'FULL_TIME' | 'PART_TIME' | 'SEASONAL';
export type JobStatus = 'OPEN' | 'CLOSED';

export interface StoreRef {
  id: number;
  code: string;
  name: string;
}

export interface JobSummary {
  id: number;
  title: string;
  slug: string;
  employmentType: EmploymentType;
  salaryText?: string | null;
  headcount?: number | null;
  /** yyyy-MM-dd; null = không hạn */
  deadline?: string | null;
  chainWide: boolean;
  /** Cơ sở đang nhận hồ sơ */
  stores: StoreRef[];
  acceptingApplications: boolean;
}

export interface JobDetail extends JobSummary {
  /** Markdown */
  description: string;
}

export interface AdminJob {
  id: number;
  title: string;
  slug: string;
  employmentType: EmploymentType;
  salaryText?: string | null;
  headcount?: number | null;
  deadline?: string | null;
  description: string;
  status: JobStatus;
  expired: boolean;
  chainWide: boolean;
  /** Cơ sở đã cấu hình; rỗng = toàn chuỗi */
  stores: StoreRef[];
  createdAt: string;
}

export interface JobPayload {
  title: string;
  slug?: string | null;
  employmentType: EmploymentType;
  salaryText?: string | null;
  headcount?: number | null;
  deadline?: string | null;
  description: string;
  status: JobStatus;
  /** Rỗng = tuyển toàn chuỗi */
  storeIds: number[];
}

export interface AdminJobFilter {
  status?: JobStatus;
  keyword?: string;
  page?: number;
  size?: number;
}

export type ApplicationStatus = 'NEW' | 'CONTACTED' | 'HIRED' | 'REJECTED';

export interface JobApplication {
  id: number;
  jobId: number;
  jobTitle: string;
  jobSlug: string;
  storeId: number;
  storeName: string;
  fullName: string;
  phone: string;
  email?: string | null;
  message?: string | null;
  hasCv: boolean;
  cvOriginalName?: string | null;
  status: ApplicationStatus;
  internalNote?: string | null;
  handledByName?: string | null;
  handledAt?: string | null;
  createdAt: string;
}

export interface ApplicationForm {
  storeId: number | null;
  fullName: string;
  phone: string;
  email: string;
  message: string;
  /** Ô bẫy bot — người thật luôn để trống */
  website: string;
}

export interface ApplicationFilter {
  jobId?: number;
  storeId?: number;
  status?: ApplicationStatus;
  page?: number;
  size?: number;
}

export interface ApplicationUpdate {
  status?: ApplicationStatus;
  internalNote?: string;
}

export type FeedbackType = 'SUGGESTION' | 'COMPLAINT' | 'PARTNERSHIP' | 'OTHER';
export type FeedbackStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED';

export interface FeedbackPayload {
  type: FeedbackType;
  storeId?: number | null;
  /** Chỉ gửi khi đăng nhập và là đơn của chính mình */
  orderCode?: string | null;
  fullName: string;
  phone?: string | null;
  email?: string | null;
  subject: string;
  content: string;
  /** Ô bẫy bot */
  website: string;
}

export interface Feedback {
  id: number;
  type: FeedbackType;
  /** null = chung toàn chuỗi */
  storeId?: number | null;
  storeName?: string | null;
  orderCode?: string | null;
  userId?: number | null;
  fullName: string;
  phone?: string | null;
  email?: string | null;
  subject: string;
  content: string;
  status: FeedbackStatus;
  resolutionNote?: string | null;
  handledByName?: string | null;
  handledAt?: string | null;
  createdAt: string;
}

export interface FeedbackFilter {
  type?: FeedbackType;
  storeId?: number;
  status?: FeedbackStatus;
  page?: number;
  size?: number;
}

export interface FeedbackUpdate {
  status?: FeedbackStatus;
  resolutionNote?: string;
}
```

- [ ] **Step 3: `frontend/src/utils/contentLabels.ts`**

```ts
import type { BadgeTone } from '../components/ui';
import type {
  ApplicationStatus,
  EmploymentType,
  FeedbackStatus,
  FeedbackType,
  JobStatus,
  NewsDisplayState,
  StoreRef,
} from '../types/content';
import { formatDate } from './formatters';

export const NEWS_STATE_LABEL: Record<NewsDisplayState, string> = {
  DRAFT: 'Nháp',
  SCHEDULED: 'Hẹn giờ',
  PUBLISHED: 'Đã đăng',
};

export const NEWS_STATE_TONE: Record<NewsDisplayState, BadgeTone> = {
  DRAFT: 'neutral',
  SCHEDULED: 'info',
  PUBLISHED: 'success',
};

export const EMPLOYMENT_TYPE_LABEL: Record<EmploymentType, string> = {
  FULL_TIME: 'Toàn thời gian',
  PART_TIME: 'Bán thời gian',
  SEASONAL: 'Thời vụ',
};

export const JOB_STATUS_LABEL: Record<JobStatus, string> = {
  OPEN: 'Đang mở',
  CLOSED: 'Đã đóng',
};

export const APPLICATION_STATUS_LABEL: Record<ApplicationStatus, string> = {
  NEW: 'Mới',
  CONTACTED: 'Đã liên hệ',
  HIRED: 'Đã nhận',
  REJECTED: 'Từ chối',
};

export const APPLICATION_STATUS_TONE: Record<ApplicationStatus, BadgeTone> = {
  NEW: 'warning',
  CONTACTED: 'info',
  HIRED: 'success',
  REJECTED: 'neutral',
};

export const FEEDBACK_TYPE_LABEL: Record<FeedbackType, string> = {
  SUGGESTION: 'Góp ý',
  COMPLAINT: 'Khiếu nại',
  PARTNERSHIP: 'Hợp tác',
  OTHER: 'Khác',
};

export const FEEDBACK_STATUS_LABEL: Record<FeedbackStatus, string> = {
  NEW: 'Mới',
  IN_PROGRESS: 'Đang xử lý',
  RESOLVED: 'Đã xử lý',
};

export const FEEDBACK_STATUS_TONE: Record<FeedbackStatus, BadgeTone> = {
  NEW: 'warning',
  IN_PROGRESS: 'info',
  RESOLVED: 'success',
};

/** "Toàn chuỗi" hoặc tên các cơ sở, cách nhau bởi dấu phẩy */
export const storesText = (chainWide: boolean, stores: StoreRef[]): string =>
  chainWide ? 'Toàn chuỗi' : stores.map((store) => store.name).join(', ');

/** Hạn nộp dd/MM/yyyy; null = không giới hạn */
export const deadlineText = (deadline?: string | null): string =>
  deadline ? formatDate(deadline) : 'Không giới hạn';

/** Cùng quy tắc với trang đăng ký và backend ContactFields */
export const VN_PHONE_REGEX = /^(0|\+84)(3|5|7|8|9)[0-9]{8}$/;
export const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const normalizePhone = (raw: string): string => raw.replace(/[\s.-]/g, '');
```

- [ ] **Step 4: `frontend/src/utils/inboxEvents.ts`**

```ts
/** Phát khi hồ sơ / phản hồi vừa được xử lý để huy hiệu số NEW trên menu tải lại ngay. */
export const INBOX_CHANGED_EVENT = 'bmk:inbox-changed';

export const notifyInboxChanged = (): void => {
  window.dispatchEvent(new Event(INBOX_CHANGED_EVENT));
};
```

- [ ] **Step 5: `frontend/src/api/newsApi.ts`**

```ts
import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { AdminNews, AdminNewsFilter, NewsDetail, NewsPayload, NewsSummary } from '../types/content';

export const newsApi = {
  /** Bài đã đăng: ghim trước, mới nhất trước */
  async listPublished(page = 0, size = 9): Promise<PageResponse<NewsSummary>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<NewsSummary>>>('/news', { params: { page, size } });
    return res.data.data;
  },
  async latest(limit = 3): Promise<NewsSummary[]> {
    const res = await axiosClient.get<ApiResponse<NewsSummary[]>>('/news/latest', { params: { limit } });
    return res.data.data;
  },
  async getBySlug(slug: string): Promise<NewsDetail> {
    const res = await axiosClient.get<ApiResponse<NewsDetail>>(`/news/${encodeURIComponent(slug)}`);
    return res.data.data;
  },
  async adminList(filter: AdminNewsFilter = {}): Promise<PageResponse<AdminNews>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<AdminNews>>>('/admin/news', { params: filter });
    return res.data.data;
  },
  async adminGet(id: number): Promise<AdminNews> {
    const res = await axiosClient.get<ApiResponse<AdminNews>>(`/admin/news/${id}`);
    return res.data.data;
  },
  async adminCreate(payload: NewsPayload): Promise<AdminNews> {
    const res = await axiosClient.post<ApiResponse<AdminNews>>('/admin/news', payload);
    return res.data.data;
  },
  async adminUpdate(id: number, payload: NewsPayload): Promise<AdminNews> {
    const res = await axiosClient.put<ApiResponse<AdminNews>>(`/admin/news/${id}`, payload);
    return res.data.data;
  },
  async adminDelete(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/admin/news/${id}`);
  },
};
```

- [ ] **Step 6: `frontend/src/api/jobApi.ts`**

```ts
import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type {
  AdminJob,
  AdminJobFilter,
  ApplicationFilter,
  ApplicationForm,
  ApplicationUpdate,
  JobApplication,
  JobDetail,
  JobPayload,
  JobStatus,
  JobSummary,
} from '../types/content';

export const jobApi = {
  /** Tin đang tuyển; storeId lọc tin của cơ sở đó + tin toàn chuỗi */
  async listOpen(storeId?: number | null): Promise<JobSummary[]> {
    const res = await axiosClient.get<ApiResponse<JobSummary[]>>('/jobs', {
      params: storeId ? { storeId } : {},
    });
    return res.data.data;
  },
  async getBySlug(slug: string): Promise<JobDetail> {
    const res = await axiosClient.get<ApiResponse<JobDetail>>(`/jobs/${encodeURIComponent(slug)}`);
    return res.data.data;
  },
  /** Nộp hồ sơ (multipart). Trả câu thông báo của backend. */
  async apply(slug: string, form: ApplicationForm, cv: File | null): Promise<string> {
    const data = new FormData();
    if (form.storeId != null) data.append('storeId', String(form.storeId));
    data.append('fullName', form.fullName);
    data.append('phone', form.phone);
    data.append('email', form.email);
    data.append('message', form.message);
    data.append('website', form.website);
    if (cv) data.append('cv', cv);
    // PHẢI set tường minh: axiosClient mặc định application/json (xem siteSettingsApi.uploadHeroImage)
    const res = await axiosClient.post<ApiResponse<void>>(`/jobs/${encodeURIComponent(slug)}/applications`, data, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return res.data.message ?? 'Đã gửi hồ sơ ứng tuyển.';
  },
  async adminList(filter: AdminJobFilter = {}): Promise<PageResponse<AdminJob>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<AdminJob>>>('/admin/jobs', { params: filter });
    return res.data.data;
  },
  async adminGet(id: number): Promise<AdminJob> {
    const res = await axiosClient.get<ApiResponse<AdminJob>>(`/admin/jobs/${id}`);
    return res.data.data;
  },
  async adminCreate(payload: JobPayload): Promise<AdminJob> {
    const res = await axiosClient.post<ApiResponse<AdminJob>>('/admin/jobs', payload);
    return res.data.data;
  },
  async adminUpdate(id: number, payload: JobPayload): Promise<AdminJob> {
    const res = await axiosClient.put<ApiResponse<AdminJob>>(`/admin/jobs/${id}`, payload);
    return res.data.data;
  },
  async adminSetStatus(id: number, status: JobStatus): Promise<AdminJob> {
    const res = await axiosClient.patch<ApiResponse<AdminJob>>(`/admin/jobs/${id}/status`, { status });
    return res.data.data;
  },
  async adminDelete(id: number): Promise<void> {
    await axiosClient.delete<ApiResponse<void>>(`/admin/jobs/${id}`);
  },
  /** MANAGER bị backend ép về cơ sở của mình */
  async listApplications(filter: ApplicationFilter = {}): Promise<PageResponse<JobApplication>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<JobApplication>>>('/job-applications', {
      params: filter,
    });
    return res.data.data;
  },
  async getApplication(id: number): Promise<JobApplication> {
    const res = await axiosClient.get<ApiResponse<JobApplication>>(`/job-applications/${id}`);
    return res.data.data;
  },
  async updateApplication(id: number, update: ApplicationUpdate): Promise<JobApplication> {
    const res = await axiosClient.patch<ApiResponse<JobApplication>>(`/job-applications/${id}`, update);
    return res.data.data;
  },
  /** CV chỉ tải qua API có token — không có đường dẫn công khai */
  async downloadCv(application: JobApplication): Promise<void> {
    const res = await axiosClient.get<Blob>(`/job-applications/${application.id}/cv`, { responseType: 'blob' });
    const url = URL.createObjectURL(res.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = application.cvOriginalName || `cv-ho-so-${application.id}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  },
  async countNewApplications(): Promise<number> {
    const res = await axiosClient.get<ApiResponse<number>>('/job-applications/count-new');
    return res.data.data;
  },
};
```

- [ ] **Step 7: `frontend/src/api/feedbackApi.ts`**

```ts
import { axiosClient } from './axiosClient';
import type { ApiResponse } from '../types/auth';
import type { PageResponse } from '../types/admin';
import type { Feedback, FeedbackFilter, FeedbackPayload, FeedbackUpdate } from '../types/content';

export const feedbackApi = {
  /** Công khai; có token thì backend ghi nhận người gửi (bắt buộc khi gắn đơn). Trả câu thông báo. */
  async submit(payload: FeedbackPayload): Promise<string> {
    const res = await axiosClient.post<ApiResponse<void>>('/feedbacks', payload);
    return res.data.message ?? 'Đã gửi phản hồi.';
  },
  async adminList(filter: FeedbackFilter = {}): Promise<PageResponse<Feedback>> {
    const res = await axiosClient.get<ApiResponse<PageResponse<Feedback>>>('/admin/feedbacks', { params: filter });
    return res.data.data;
  },
  async adminGet(id: number): Promise<Feedback> {
    const res = await axiosClient.get<ApiResponse<Feedback>>(`/admin/feedbacks/${id}`);
    return res.data.data;
  },
  async adminUpdate(id: number, update: FeedbackUpdate): Promise<Feedback> {
    const res = await axiosClient.patch<ApiResponse<Feedback>>(`/admin/feedbacks/${id}`, update);
    return res.data.data;
  },
  async countNew(): Promise<number> {
    const res = await axiosClient.get<ApiResponse<number>>('/admin/feedbacks/count-new');
    return res.data.data;
  },
};
```

- [ ] **Step 8: `frontend/src/components/content/MarkdownView.tsx`**

```tsx
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import '../../styles/components/content.css';

const isExternal = (href?: string): boolean =>
  Boolean(href) && /^https?:\/\//i.test(href as string) && !(href as string).startsWith(window.location.origin);

/**
 * Chỉ truyền các thuộc tính cần thiết xuống thẻ DOM (không spread props) để không lọt `node`
 * của react-markdown ra HTML.
 */
const COMPONENTS: Components = {
  a: ({ href, title, children }) =>
    isExternal(href) ? (
      <a href={href} title={title} target="_blank" rel="noopener noreferrer">
        {children}
      </a>
    ) : (
      <a href={href} title={title}>
        {children}
      </a>
    ),
  img: ({ src, alt, title }) => <img src={typeof src === 'string' ? src : undefined} alt={alt ?? ''} title={title} loading="lazy" />,
};

export interface MarkdownViewProps {
  source: string;
}

/**
 * Hiển thị Markdown an toàn (spec D1): không bật HTML thô, rehype-sanitize lọc thuộc tính/URL nguy hiểm,
 * link ra ngoài mở tab mới với rel="noopener noreferrer". Dùng chung cho tin tức và tuyển dụng.
 */
export const MarkdownView = ({ source }: MarkdownViewProps) => (
  <div className="md-view">
    <ReactMarkdown rehypePlugins={[rehypeSanitize]} components={COMPONENTS}>
      {source}
    </ReactMarkdown>
  </div>
);
```

- [ ] **Step 9: `frontend/src/components/content/MarkdownEditor.tsx`**

```tsx
import { useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { Bold, Heading2, Heading3, ImagePlus, Italic, Link as LinkIcon, List } from 'lucide-react';
import { Tabs, useToast } from '../ui';
import { MarkdownView } from './MarkdownView';
import '../../styles/components/content.css';

const MAX_IMAGE_MB = 5;

type EditorTab = 'write' | 'preview';

export interface MarkdownEditorProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
  /** Tải ảnh lên và trả về URL công khai — có thì hiện nút "Chèn ảnh" */
  onUploadImage?: (file: File) => Promise<string>;
  rows?: number;
  placeholder?: string;
}

/** Trình soạn Markdown đơn giản (spec D1): thanh công cụ chèn cú pháp + tab Xem trước. */
export const MarkdownEditor = ({ label, value, onChange, onUploadImage, rows = 14, placeholder }: MarkdownEditorProps) => {
  const toast = useToast();
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [tab, setTab] = useState<EditorTab>('write');
  const [isUploading, setIsUploading] = useState(false);

  /** Thay vùng đang chọn bằng `text`, đặt con trỏ sau đoạn chèn */
  const replaceSelection = (build: (selected: string, start: number) => string) => {
    const el = textareaRef.current;
    const start = el ? el.selectionStart : value.length;
    const end = el ? el.selectionEnd : value.length;
    const text = build(value.slice(start, end), start);
    onChange(value.slice(0, start) + text + value.slice(end));
    window.requestAnimationFrame(() => {
      if (!el) return;
      el.focus();
      const caret = start + text.length;
      el.setSelectionRange(caret, caret);
    });
  };

  const wrap = (before: string, after: string, placeholderText: string) =>
    replaceSelection((selected) => `${before}${selected || placeholderText}${after}`);

  const prefixLines = (prefix: string, placeholderText: string) =>
    replaceSelection((selected, start) => {
      const lead = start > 0 && value[start - 1] !== '\n' ? '\n' : '';
      const lines = (selected || placeholderText).split('\n').map((line) => `${prefix}${line}`);
      return `${lead}${lines.join('\n')}`;
    });

  const handleImage = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !onUploadImage) return;
    if (!file.type.startsWith('image/')) {
      toast.error('Tệp chèn phải là ảnh (PNG, JPG, WEBP, GIF).');
      return;
    }
    if (file.size > MAX_IMAGE_MB * 1024 * 1024) {
      toast.error(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
      return;
    }
    setIsUploading(true);
    try {
      const url = await onUploadImage(file);
      replaceSelection((_selected, start) => `${start > 0 && value[start - 1] !== '\n' ? '\n' : ''}![Mô tả ảnh](${url})\n`);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Tải ảnh lên thất bại');
    } finally {
      setIsUploading(false);
    }
  };

  const tools = [
    { label: 'In đậm', icon: <Bold size={16} />, run: () => wrap('**', '**', 'chữ đậm') },
    { label: 'In nghiêng', icon: <Italic size={16} />, run: () => wrap('_', '_', 'chữ nghiêng') },
    { label: 'Tiêu đề lớn (H2)', icon: <Heading2 size={16} />, run: () => prefixLines('## ', 'Tiêu đề') },
    { label: 'Tiêu đề nhỏ (H3)', icon: <Heading3 size={16} />, run: () => prefixLines('### ', 'Tiêu đề nhỏ') },
    { label: 'Danh sách', icon: <List size={16} />, run: () => prefixLines('- ', 'Mục') },
    { label: 'Chèn link', icon: <LinkIcon size={16} />, run: () => wrap('[', '](https://)', 'chữ hiển thị') },
  ];

  return (
    <div className="md-editor">
      <span className="ui-field__label">{label}</span>
      <Tabs<EditorTab>
        tabs={[
          { key: 'write', label: 'Soạn' },
          { key: 'preview', label: 'Xem trước' },
        ]}
        value={tab}
        onChange={setTab}
      />

      {tab === 'write' ? (
        <>
          <div className="md-editor__toolbar" role="toolbar" aria-label="Định dạng Markdown">
            {tools.map((tool) => (
              <button key={tool.label} type="button" className="md-editor__btn" title={tool.label} aria-label={tool.label} onClick={tool.run}>
                {tool.icon}
              </button>
            ))}
            {onUploadImage && (
              <>
                <button
                  type="button"
                  className="md-editor__btn"
                  title="Chèn ảnh"
                  aria-label="Chèn ảnh"
                  disabled={isUploading}
                  onClick={() => fileRef.current?.click()}
                >
                  <ImagePlus size={16} />
                </button>
                <input ref={fileRef} type="file" accept="image/*" hidden onChange={(event) => void handleImage(event)} />
              </>
            )}
            {isUploading && <span className="md-editor__hint">Đang tải ảnh…</span>}
          </div>
          <textarea
            ref={textareaRef}
            className="ui-field__input md-editor__area"
            rows={rows}
            value={value}
            placeholder={placeholder}
            onChange={(event) => onChange(event.target.value)}
          />
          <span className="md-editor__hint">
            Hỗ trợ **đậm**, _nghiêng_, ## tiêu đề, - danh sách, [link](https://…), ![ảnh](url). Không chèn được HTML.
          </span>
        </>
      ) : (
        <div className="md-editor__preview">
          {value.trim() ? <MarkdownView source={value} /> : <p className="md-editor__hint">Chưa có nội dung để xem trước.</p>}
        </div>
      )}
    </div>
  );
};
```

- [ ] **Step 10: `frontend/src/styles/components/content.css`**

```css
/* ==========================================================================
   Nội dung: tin tức, tuyển dụng, phản hồi (dự án con D)
   ========================================================================== */

/* ---- Markdown hiển thị ---- */
.md-view {
  color: var(--stone-800);
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.md-view h2 { font-size: 1.35rem; margin: 1.4em 0 .5em; }
.md-view h3 { font-size: 1.12rem; margin: 1.2em 0 .4em; }
.md-view p { margin: 0 0 .9em; }
.md-view ul, .md-view ol { margin: 0 0 1em; padding-left: 1.4em; }
.md-view a { color: var(--primary-700); text-decoration: underline; }
.md-view img { max-width: 100%; height: auto; border-radius: var(--radius-md); margin: .5em 0; }
.md-view blockquote { margin: 0 0 1em; padding-left: 12px; border-left: 3px solid var(--primary-300); color: var(--stone-600); }
.md-view code { background: var(--stone-100); padding: 1px 5px; border-radius: 4px; font-size: .92em; }

/* ---- Trình soạn ---- */
.md-editor { display: flex; flex-direction: column; gap: 8px; }
.md-editor__toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.md-editor__btn {
  display: inline-grid; place-items: center; width: 34px; height: 34px;
  border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface);
  color: var(--stone-700); cursor: pointer;
}
.md-editor__btn:hover:not(:disabled) { background: var(--primary-50); color: var(--primary-700); }
.md-editor__btn:disabled { opacity: .5; cursor: progress; }
.md-editor__area { min-height: 220px; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 14px; resize: vertical; }
.md-editor__preview { min-height: 220px; padding: 12px 14px; border: 1px dashed var(--border); border-radius: var(--radius-md); background: var(--bg-subtle); }
.md-editor__hint { font-size: 12.5px; color: var(--stone-500); }

/* ---- Tin tức ---- */
.news-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 20px; }
.news-card {
  display: flex; flex-direction: column; overflow: hidden;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg, 14px);
  color: inherit; text-decoration: none; transition: box-shadow .2s, transform .2s;
}
.news-card:hover { box-shadow: 0 8px 24px rgba(28, 25, 23, .08); transform: translateY(-2px); }
.news-card__cover { aspect-ratio: 16 / 9; background: var(--primary-50); display: grid; place-items: center; color: var(--primary-600); }
.news-card__cover img { width: 100%; height: 100%; object-fit: cover; }
.news-card__body { display: flex; flex-direction: column; gap: 6px; padding: 14px 16px 18px; }
.news-card__meta { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--stone-500); }
.news-card__title { margin: 0; font-size: 1.05rem; line-height: 1.4; }
.news-card__summary { margin: 0; color: var(--stone-600); font-size: 14px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.news-article { max-width: 760px; margin: 0 auto; }
.news-article__cover { width: 100%; max-height: 420px; object-fit: cover; border-radius: var(--radius-lg, 14px); margin-bottom: 18px; }
.news-article__date { color: var(--stone-500); font-size: 14px; margin: 4px 0 16px; }
.news-related { margin-top: 36px; }
.news-related h2 { font-size: 1.2rem; margin: 0 0 14px; }

/* ---- Tuyển dụng ---- */
.job-toolbar { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px; margin-bottom: 18px; }
.job-toolbar .ui-field { min-width: 240px; }
.job-list { display: grid; gap: 14px; }
.job-card {
  display: grid; gap: 6px; padding: 16px 18px; color: inherit; text-decoration: none;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg, 14px);
}
.job-card:hover { border-color: var(--primary-300); }
.job-card__title { margin: 0; font-size: 1.08rem; }
.job-card__facts { display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: 14px; color: var(--stone-600); }
.job-card__facts span { display: inline-flex; align-items: center; gap: 6px; }
.job-detail { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 24px; align-items: start; }
.job-detail__facts { display: grid; gap: 10px; font-size: 14.5px; }
.job-detail__facts dt { color: var(--stone-500); font-size: 12.5px; }
.job-detail__facts dd { margin: 0; font-weight: 600; }
@media (max-width: 900px) {
  .job-detail { grid-template-columns: 1fr; }
}

/* ---- Form công khai (ứng tuyển, phản hồi) ---- */
.cf-form { display: grid; gap: 14px; }
.cf-form__row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.cf-form__note { font-size: 13px; color: var(--stone-500); margin: 0; }
.cf-form__success { display: grid; gap: 10px; justify-items: start; padding: 18px; border-radius: var(--radius-md); background: var(--success-bg); color: var(--success); }
@media (max-width: 640px) {
  .cf-form__row { grid-template-columns: 1fr; }
}
/* Ô bẫy bot: ẩn khỏi người dùng nhưng vẫn có trong DOM (không dùng display:none để bot vẫn điền) */
.hp-field { position: absolute; left: -10000px; top: auto; width: 1px; height: 1px; overflow: hidden; }

/* ---- Hộp hồ sơ / phản hồi (quản lý) ---- */
.inbox-filters { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px; margin-bottom: 14px; }
.inbox-filters .ui-field { min-width: 200px; }
.inbox { display: grid; grid-template-columns: minmax(0, 1fr) 380px; gap: 18px; align-items: start; }
.inbox--single { grid-template-columns: minmax(0, 1fr); }
.inbox__row { cursor: pointer; }
.inbox__row--active td { background: var(--primary-50); }
.inbox__detail { position: sticky; top: 16px; display: grid; gap: 12px; }
.inbox__detail dl { display: grid; grid-template-columns: 110px 1fr; gap: 6px 10px; margin: 0; font-size: 14px; }
.inbox__detail dt { color: var(--stone-500); }
.inbox__detail dd { margin: 0; overflow-wrap: anywhere; }
.inbox__message { white-space: pre-wrap; background: var(--bg-subtle); border-radius: var(--radius-md); padding: 10px 12px; font-size: 14px; }
.inbox__actions { display: flex; flex-wrap: wrap; gap: 8px; }
@media (max-width: 1100px) {
  .inbox { grid-template-columns: 1fr; }
  .inbox__detail { position: static; }
}
.order-peek { border: 1px dashed var(--border); border-radius: var(--radius-md); padding: 10px 12px; font-size: 13.5px; display: grid; gap: 4px; }
```

- [ ] **Step 11: Cổng kiểm frontend**

Run (trong `frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → vẫn 26 cảnh báo (file mới chỉ export component hoặc chỉ export hằng/hàm, không trộn).

- [ ] **Step 12: Commit (người dùng tự chạy)**

```
git add frontend/package.json frontend/package-lock.json frontend/src/types/content.ts frontend/src/utils/contentLabels.ts frontend/src/utils/inboxEvents.ts frontend/src/api/newsApi.ts frontend/src/api/jobApi.ts frontend/src/api/feedbackApi.ts frontend/src/components/content frontend/src/styles/components/content.css
git commit -m "feat(frontend): react-markdown + rehype-sanitize, kiểu dữ liệu và API tin tức/tuyển dụng/phản hồi, trình soạn và hiển thị Markdown an toàn"
```

---

## Task 11: Frontend — trang Tin tức, chi tiết bài, khối "Tin mới" trên trang chủ, link header/footer

**Files:**
- Create (gốc `frontend/src/`): `components/content/NewsCard.tsx`, `components/home/LatestNewsSection.tsx`, `pages/news/NewsListPage.tsx`, `pages/news/NewsDetailPage.tsx`
- Modify: `App.tsx` (route `tin-tuc`, `tin-tuc/:slug`), `pages/LandingPage.tsx` (chèn khối Tin mới), `components/layout/CustomerLayout.tsx` (link **Tin tức** ở header + footer)

**Interfaces:**
- Consumes: `newsApi.listPublished/latest/getBySlug`, `NewsSummary`, `NewsDetail`, `MarkdownView` (Task 10); `PageResponse`; UI `EmptyState`, `Pagination`, `Skeleton`; `formatDate`, `formatDateTime`.
- Produces: `<NewsCard news={NewsSummary} />`, `<LatestNewsSection />` (tự ẩn khi không có bài), `NewsListPage` (route `/tin-tuc`), `NewsDetailPage` (route `/tin-tuc/:slug`).

- [ ] **Step 1: `frontend/src/components/content/NewsCard.tsx`**

```tsx
import { Link } from 'react-router-dom';
import { Newspaper, Pin } from 'lucide-react';
import { formatDate } from '../../utils/formatters';
import type { NewsSummary } from '../../types/content';
import '../../styles/components/content.css';

export interface NewsCardProps {
  news: NewsSummary;
}

/** Thẻ bài: ảnh bìa, ngày đăng, tiêu đề, tóm tắt — dùng cho lưới tin, khối Tin mới, bài liên quan. */
export const NewsCard = ({ news }: NewsCardProps) => (
  <Link to={`/tin-tuc/${news.slug}`} className="news-card">
    <div className="news-card__cover">
      {news.coverImageUrl ? <img src={news.coverImageUrl} alt="" loading="lazy" /> : <Newspaper size={40} />}
    </div>
    <div className="news-card__body">
      <div className="news-card__meta">
        {news.pinned && (
          <>
            <Pin size={13} aria-label="Bài ghim" />
            Nổi bật ·
          </>
        )}
        <span>{formatDate(news.publishedAt)}</span>
      </div>
      <h3 className="news-card__title">{news.title}</h3>
      {news.summary && <p className="news-card__summary">{news.summary}</p>}
    </div>
  </Link>
);
```

- [ ] **Step 2: `frontend/src/pages/news/NewsListPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Newspaper } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { EmptyState, Pagination, Skeleton } from '../../components/ui';
import { NewsCard } from '../../components/content/NewsCard';
import type { PageResponse } from '../../types/admin';
import type { NewsSummary } from '../../types/content';
import '../../styles/components/card.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 9;

/** /tin-tuc — lưới bài đã đăng (ghim trước, mới nhất trước), phân trang. */
export const NewsListPage = () => {
  const [page, setPage] = useState(1);
  const [data, setData] = useState<PageResponse<NewsSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    newsApi
      .listPublished(page - 1, PAGE_SIZE)
      .then((res) => {
        if (!alive) return;
        setData(res);
        setError(null);
      })
      .catch((err) => {
        if (alive) setError(err instanceof Error ? err.message : 'Không tải được tin tức');
      });
    return () => {
      alive = false;
    };
  }, [page]);

  const changePage = (next: number) => {
    setPage(next);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / Tin tức
          </p>
          <h1 className="page-bar__title">Tin tức &amp; khuyến mãi</h1>
        </div>
      </div>

      {error ? (
        <EmptyState icon={<Newspaper size={28} />} title="Không tải được tin tức" description={error} />
      ) : data === null ? (
        <div className="news-grid">
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} variant="card" />
          ))}
        </div>
      ) : data.content.length === 0 ? (
        <EmptyState
          icon={<Newspaper size={28} />}
          title="Chưa có tin nào"
          description="Tin tức và chương trình khuyến mãi của cửa hàng sẽ xuất hiện tại đây."
        />
      ) : (
        <>
          <div className="news-grid">
            {data.content.map((news) => (
              <NewsCard key={news.id} news={news} />
            ))}
          </div>
          <Pagination page={page} totalPages={data.totalPages} onChange={changePage} />
        </>
      )}
    </div>
  );
};
```

- [ ] **Step 3: `frontend/src/pages/news/NewsDetailPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Newspaper } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { EmptyState, Skeleton } from '../../components/ui';
import { MarkdownView } from '../../components/content/MarkdownView';
import { NewsCard } from '../../components/content/NewsCard';
import { formatDateTime } from '../../utils/formatters';
import type { NewsDetail } from '../../types/content';
import '../../styles/components/card.css';
import '../../styles/components/content.css';

interface LoadState {
  slug: string;
  news: NewsDetail | null;
  error: string | null;
}

/** /tin-tuc/:slug — bài đã đăng + tối đa 3 bài liên quan. Bài nháp/hẹn giờ: backend trả 404. */
export const NewsDetailPage = () => {
  const { slug = '' } = useParams();
  const [state, setState] = useState<LoadState | null>(null);

  useEffect(() => {
    let alive = true;
    newsApi
      .getBySlug(slug)
      .then((news) => {
        if (alive) setState({ slug, news, error: null });
      })
      .catch((err) => {
        if (alive) setState({ slug, news: null, error: err instanceof Error ? err.message : 'Không tìm thấy bài viết' });
      });
    return () => {
      alive = false;
    };
  }, [slug]);

  if (state === null || state.slug !== slug) {
    return (
      <div className="news-article">
        <Skeleton variant="card" />
      </div>
    );
  }

  if (!state.news) {
    return (
      <EmptyState
        icon={<Newspaper size={28} />}
        title="Không tìm thấy bài viết"
        description={state.error ?? 'Bài viết không tồn tại hoặc chưa được đăng.'}
        action={
          <Link className="ui-btn ui-btn--primary ui-btn--md" to="/tin-tuc">
            Xem tất cả tin tức
          </Link>
        }
      />
    );
  }

  const news = state.news;
  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / <Link to="/tin-tuc">Tin tức</Link> / {news.title}
          </p>
        </div>
      </div>

      <article className="card news-article">
        <div className="card__body">
          {news.coverImageUrl && <img className="news-article__cover" src={news.coverImageUrl} alt="" />}
          <h1 className="page-bar__title">{news.title}</h1>
          <p className="news-article__date">Đăng lúc {formatDateTime(news.publishedAt)}</p>
          <MarkdownView source={news.content} />
        </div>
      </article>

      {news.related.length > 0 && (
        <section className="news-related">
          <h2>Bài liên quan</h2>
          <div className="news-grid">
            {news.related.map((item) => (
              <NewsCard key={item.id} news={item} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
};
```

- [ ] **Step 4: `frontend/src/components/home/LatestNewsSection.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Newspaper } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { NewsCard } from '../content/NewsCard';
import type { NewsSummary } from '../../types/content';
import '../../styles/components/menu.css';
import '../../styles/components/content.css';

/** Khối "Tin mới" trên trang chủ — 3 bài mới nhất; không có bài hoặc lỗi mạng thì ẩn hẳn. */
export const LatestNewsSection = () => {
  const [items, setItems] = useState<NewsSummary[] | null>(null);

  useEffect(() => {
    let alive = true;
    newsApi
      .latest(3)
      .then((res) => {
        if (alive) setItems(res);
      })
      .catch(() => {
        if (alive) setItems([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  if (!items || items.length === 0) return null;

  return (
    <section className="menu__section" id="home-news">
      <div className="menu__section-head">
        <div>
          <h2 className="menu__section-title">
            <Newspaper size={22} />
            Tin mới
          </h2>
          <p className="menu__section-sub">Khuyến mãi và hoạt động mới nhất của cửa hàng</p>
        </div>
        <Link className="menu__section-link" to="/tin-tuc">
          Xem tất cả tin
        </Link>
      </div>
      <div className="news-grid">
        {items.map((news) => (
          <NewsCard key={news.id} news={news} />
        ))}
      </div>
    </section>
  );
};
```

- [ ] **Step 5: Trang chủ — chèn khối Tin mới**

Trong `frontend/src/pages/LandingPage.tsx`:
- thêm import `import { LatestNewsSection } from '../components/home/LatestNewsSection';`
- sửa

```tsx
      <HeroSection />
      <FeaturedStrip />
      <PromiseGrid />
```

thành

```tsx
      <HeroSection />
      <FeaturedStrip />
      <LatestNewsSection />
      <PromiseGrid />
```

- [ ] **Step 6: Route trong `frontend/src/App.tsx`**

Thêm import:

```tsx
import { NewsListPage } from './pages/news/NewsListPage';
import { NewsDetailPage } from './pages/news/NewsDetailPage';
```

Trong khối `<Route element={<CustomerLayout />}>`, ngay sau `<Route path="stores" element={<StoresPage />} />` thêm:

```tsx
                <Route path="tin-tuc" element={<NewsListPage />} />
                <Route path="tin-tuc/:slug" element={<NewsDetailPage />} />
```

- [ ] **Step 7: Link Tin tức ở header và footer (`CustomerLayout.tsx`)**

Header — sửa

```tsx
            <NavLink to="/stores" className={navLinkClass}>
              Cửa hàng
            </NavLink>
```

thành

```tsx
            <NavLink to="/stores" className={navLinkClass}>
              Cửa hàng
            </NavLink>
            <NavLink to="/tin-tuc" className={navLinkClass}>
              Tin tức
            </NavLink>
```

Footer cột "Khám phá" — sửa

```tsx
            <Link to="/stores">Hệ thống cửa hàng</Link>
```

thành

```tsx
            <Link to="/stores">Hệ thống cửa hàng</Link>
            <Link to="/tin-tuc">Tin tức</Link>
```

- [ ] **Step 8: Cổng kiểm + bấm thử**

Run (trong `frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → 26 cảnh báo.

Bấm thử (`npm run dev`, backend đang chạy, ADMIN tạo sẵn 1 bài đã đăng + 1 bài nháp bằng Swagger `POST /api/v1/admin/news`):
- `/` có khối **Tin mới** (ẩn khi chưa có bài), bấm thẻ → `/tin-tuc/<slug>`.
- `/tin-tuc` hiện bài đã đăng, bài ghim đứng đầu; không có bài nháp.
- `/tin-tuc/<slug-bài-nháp>` → "Không tìm thấy bài viết".
- Nội dung có `[link](https://example.com)` mở tab mới; chèn `<script>` trong Markdown hiện thành chữ, không chạy.
- Header và footer có link **Tin tức**.

- [ ] **Step 9: Commit (người dùng tự chạy)**

```
git add frontend/src/components/content/NewsCard.tsx frontend/src/components/home/LatestNewsSection.tsx frontend/src/pages/news frontend/src/pages/LandingPage.tsx frontend/src/App.tsx frontend/src/components/layout/CustomerLayout.tsx
git commit -m "feat(frontend): trang tin tức, chi tiết bài + bài liên quan, khối Tin mới trên trang chủ, link Tin tức"
```

---

## Task 12: Frontend — trang Tuyển dụng, chi tiết tin + form ứng tuyển kèm CV, link header/footer

**Files:**
- Create (gốc `frontend/src/`): `components/careers/ApplyForm.tsx`, `pages/careers/JobsPage.tsx`, `pages/careers/JobDetailPage.tsx`
- Modify: `App.tsx` (route `tuyen-dung`, `tuyen-dung/:slug`), `components/layout/CustomerLayout.tsx` (link **Tuyển dụng** ở header + footer)

**Interfaces:**
- Consumes: `jobApi.listOpen/getBySlug/apply`, `JobSummary`, `JobDetail`, `ApplicationForm`, `MarkdownView`, `EMPLOYMENT_TYPE_LABEL`, `storesText`, `deadlineText`, `VN_PHONE_REGEX`, `EMAIL_REGEX`, `normalizePhone` (Task 10); `storeApi.listPublic()`, `PublicStore`; `useAuth`; UI `Button`, `Input`, `Select`, `Textarea`, `EmptyState`, `Skeleton`, `Badge`, `useToast`.
- Produces: `<ApplyForm job={JobDetail} />`, `JobsPage` (route `/tuyen-dung`), `JobDetailPage` (route `/tuyen-dung/:slug`).

- [ ] **Step 1: `frontend/src/components/careers/ApplyForm.tsx`**

```tsx
import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Send } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { useAuth } from '../../context/useAuth';
import { Button, Input, Select, Textarea, useToast } from '../ui';
import { EMAIL_REGEX, VN_PHONE_REGEX, normalizePhone } from '../../utils/contentLabels';
import type { ApplicationForm, JobDetail } from '../../types/content';
import '../../styles/components/content.css';

const MAX_CV_MB = 5;
const CV_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png'];

type FormErrors = Partial<Record<keyof ApplicationForm | 'cv', string>>;

export interface ApplyFormProps {
  job: JobDetail;
}

/** Form ứng tuyển công khai (spec D2): CV không bắt buộc, có ô bẫy bot ẩn "website". */
export const ApplyForm = ({ job }: ApplyFormProps) => {
  const { user } = useAuth();
  const toast = useToast();
  const [form, setForm] = useState<ApplicationForm>(() => ({
    storeId: job.stores.length === 1 ? job.stores[0].id : null,
    fullName: user?.fullName ?? '',
    phone: user?.phone ?? '',
    email: user?.email ?? '',
    message: '',
    website: '',
  }));
  const [cv, setCv] = useState<File | null>(null);
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [doneMessage, setDoneMessage] = useState<string | null>(null);

  const setField = <K extends keyof ApplicationForm>(key: K, value: ApplicationForm[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const validate = (): boolean => {
    const next: FormErrors = {};
    if (form.storeId == null) next.storeId = 'Vui lòng chọn cơ sở muốn làm việc';
    if (!form.fullName.trim()) next.fullName = 'Vui lòng nhập họ tên';
    const phone = normalizePhone(form.phone);
    if (!phone) next.phone = 'Vui lòng nhập số điện thoại';
    else if (!VN_PHONE_REGEX.test(phone)) next.phone = 'Số điện thoại không đúng định dạng Việt Nam';
    if (form.email.trim() && !EMAIL_REGEX.test(form.email.trim())) next.email = 'Email không đúng định dạng';
    if (cv) {
      const ext = cv.name.split('.').pop()?.toLowerCase() ?? '';
      if (!CV_EXTENSIONS.includes(ext)) next.cv = 'File CV phải là PDF, JPG hoặc PNG';
      else if (cv.size > MAX_CV_MB * 1024 * 1024) next.cv = 'File CV không được vượt quá 5MB';
    }
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!validate()) return;
    setIsSubmitting(true);
    try {
      const message = await jobApi.apply(
        job.slug,
        {
          ...form,
          fullName: form.fullName.trim(),
          phone: normalizePhone(form.phone),
          email: form.email.trim(),
          message: form.message.trim(),
        },
        cv
      );
      setDoneMessage(message);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Gửi hồ sơ thất bại, vui lòng thử lại');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (doneMessage) {
    return (
      <div className="cf-form__success" role="status">
        <strong>{doneMessage}</strong>
        <span>Nếu bạn có để lại email, cửa hàng đã gửi thư xác nhận.</span>
        <Link className="ui-btn ui-btn--secondary ui-btn--sm" to="/tuyen-dung">
          Xem vị trí khác
        </Link>
      </div>
    );
  }

  return (
    <form className="cf-form" onSubmit={(event) => void handleSubmit(event)} noValidate>
      <Select
        label="Cơ sở muốn làm việc"
        required
        value={form.storeId == null ? '' : String(form.storeId)}
        onChange={(event) => setField('storeId', event.target.value ? Number(event.target.value) : null)}
        error={errors.storeId}
      >
        <option value="">— Chọn cơ sở —</option>
        {job.stores.map((store) => (
          <option key={store.id} value={store.id}>
            {store.name}
          </option>
        ))}
      </Select>

      <div className="cf-form__row">
        <Input
          label="Họ và tên"
          required
          maxLength={100}
          value={form.fullName}
          onChange={(event) => setField('fullName', event.target.value)}
          error={errors.fullName}
        />
        <Input
          label="Số điện thoại"
          required
          type="tel"
          maxLength={20}
          value={form.phone}
          onChange={(event) => setField('phone', event.target.value)}
          error={errors.phone}
        />
      </div>

      <Input
        label="Email (để nhận thư xác nhận)"
        type="email"
        maxLength={150}
        value={form.email}
        onChange={(event) => setField('email', event.target.value)}
        error={errors.email}
      />

      <Textarea
        label="Giới thiệu ngắn / lời nhắn"
        rows={4}
        maxLength={2000}
        value={form.message}
        onChange={(event) => setField('message', event.target.value)}
        hint="Ca làm mong muốn, kinh nghiệm, thời gian có thể bắt đầu…"
      />

      <label className="ui-field">
        <span className="ui-field__label">CV (không bắt buộc)</span>
        <input
          type="file"
          accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
          onChange={(event) => setCv(event.target.files?.[0] ?? null)}
        />
        <span className={`ui-field__msg${errors.cv ? ' ui-field__msg--error' : ''}`}>
          {errors.cv ?? 'PDF, JPG hoặc PNG, tối đa 5MB.'}
        </span>
      </label>

      {/* Ô bẫy bot — người dùng không thấy; có giá trị thì backend trả 200 giả và không lưu */}
      <div className="hp-field" aria-hidden="true">
        <label>
          Website
          <input
            type="text"
            name="website"
            tabIndex={-1}
            autoComplete="off"
            value={form.website}
            onChange={(event) => setField('website', event.target.value)}
          />
        </label>
      </div>

      <p className="cf-form__note">Thông tin của bạn chỉ dùng cho việc tuyển dụng của cửa hàng.</p>
      <Button type="submit" loading={isSubmitting} icon={<Send size={16} />}>
        Gửi hồ sơ
      </Button>
    </form>
  );
};
```

- [ ] **Step 2: `frontend/src/pages/careers/JobsPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Briefcase, CalendarClock, MapPin, Users, Wallet } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { storeApi } from '../../api/storeApi';
import { Badge, EmptyState, Select, Skeleton } from '../../components/ui';
import { EMPLOYMENT_TYPE_LABEL, deadlineText, storesText } from '../../utils/contentLabels';
import type { JobSummary } from '../../types/content';
import type { PublicStore } from '../../types/store';
import '../../styles/components/card.css';
import '../../styles/components/content.css';

interface LoadState {
  storeId: number | null;
  jobs: JobSummary[];
  error: string | null;
}

/** /tuyen-dung — tin đang tuyển, lọc theo cơ sở (tin toàn chuỗi luôn hiện). */
export const JobsPage = () => {
  const [stores, setStores] = useState<PublicStore[]>([]);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [state, setState] = useState<LoadState | null>(null);

  useEffect(() => {
    let alive = true;
    storeApi
      .listPublic()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setStores([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  useEffect(() => {
    let alive = true;
    jobApi
      .listOpen(storeId)
      .then((jobs) => {
        if (alive) setState({ storeId, jobs, error: null });
      })
      .catch((err) => {
        if (alive) setState({ storeId, jobs: [], error: err instanceof Error ? err.message : 'Không tải được tin tuyển dụng' });
      });
    return () => {
      alive = false;
    };
  }, [storeId]);

  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / Tuyển dụng
          </p>
          <h1 className="page-bar__title">Tuyển dụng</h1>
        </div>
      </div>

      <div className="job-toolbar">
        <Select
          label="Cơ sở"
          value={storeId == null ? '' : String(storeId)}
          onChange={(event) => setStoreId(event.target.value ? Number(event.target.value) : null)}
        >
          <option value="">Tất cả cơ sở</option>
          {stores.map((store) => (
            <option key={store.id} value={store.id}>
              {store.name}
            </option>
          ))}
        </Select>
      </div>

      {state === null || state.storeId !== storeId ? (
        <Skeleton variant="row" count={3} />
      ) : state.error ? (
        <EmptyState icon={<Briefcase size={28} />} title="Không tải được tin tuyển dụng" description={state.error} />
      ) : state.jobs.length === 0 ? (
        <EmptyState
          icon={<Briefcase size={28} />}
          title="Hiện chưa có vị trí đang tuyển"
          description="Bạn quay lại sau nhé — cửa hàng cập nhật tin tuyển dụng thường xuyên."
        />
      ) : (
        <div className="job-list">
          {state.jobs.map((job) => (
            <Link key={job.id} to={`/tuyen-dung/${job.slug}`} className="job-card">
              <h2 className="job-card__title">{job.title}</h2>
              <div className="job-card__facts">
                <span>
                  <Badge tone="info">{EMPLOYMENT_TYPE_LABEL[job.employmentType]}</Badge>
                </span>
                <span>
                  <Wallet size={15} />
                  {job.salaryText || 'Thoả thuận'}
                </span>
                <span>
                  <MapPin size={15} />
                  {storesText(job.chainWide, job.stores)}
                </span>
                <span>
                  <CalendarClock size={15} />
                  Hạn nộp: {deadlineText(job.deadline)}
                </span>
                {job.headcount != null && (
                  <span>
                    <Users size={15} />
                    Cần {job.headcount} người
                  </span>
                )}
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
};
```

- [ ] **Step 3: `frontend/src/pages/careers/JobDetailPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Briefcase, XCircle } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { EmptyState, Skeleton } from '../../components/ui';
import { MarkdownView } from '../../components/content/MarkdownView';
import { ApplyForm } from '../../components/careers/ApplyForm';
import { EMPLOYMENT_TYPE_LABEL, deadlineText, storesText } from '../../utils/contentLabels';
import type { JobDetail } from '../../types/content';
import '../../styles/components/card.css';
import '../../styles/components/alert.css';
import '../../styles/components/content.css';

interface LoadState {
  slug: string;
  job: JobDetail | null;
  error: string | null;
}

/** /tuyen-dung/:slug — mô tả Markdown + form ứng tuyển; tin đóng/hết hạn ẩn form. */
export const JobDetailPage = () => {
  const { slug = '' } = useParams();
  const [state, setState] = useState<LoadState | null>(null);

  useEffect(() => {
    let alive = true;
    jobApi
      .getBySlug(slug)
      .then((job) => {
        if (alive) setState({ slug, job, error: null });
      })
      .catch((err) => {
        if (alive) setState({ slug, job: null, error: err instanceof Error ? err.message : 'Không tìm thấy tin tuyển dụng' });
      });
    return () => {
      alive = false;
    };
  }, [slug]);

  if (state === null || state.slug !== slug) {
    return <Skeleton variant="card" />;
  }

  if (!state.job) {
    return (
      <EmptyState
        icon={<Briefcase size={28} />}
        title="Không tìm thấy tin tuyển dụng"
        description={state.error ?? undefined}
        action={
          <Link className="ui-btn ui-btn--primary ui-btn--md" to="/tuyen-dung">
            Xem các vị trí khác
          </Link>
        }
      />
    );
  }

  const job = state.job;
  return (
    <div>
      <div className="page-bar">
        <div>
          <p className="page-bar__crumb">
            <Link to="/">Trang chủ</Link> / <Link to="/tuyen-dung">Tuyển dụng</Link> / {job.title}
          </p>
          <h1 className="page-bar__title">{job.title}</h1>
        </div>
      </div>

      <div className="job-detail">
        <article className="card">
          <div className="card__body">
            <MarkdownView source={job.description} />
          </div>
        </article>

        <aside className="card">
          <div className="card__body">
            <dl className="job-detail__facts">
              <div>
                <dt>Hình thức</dt>
                <dd>{EMPLOYMENT_TYPE_LABEL[job.employmentType]}</dd>
              </div>
              <div>
                <dt>Mức lương</dt>
                <dd>{job.salaryText || 'Thoả thuận'}</dd>
              </div>
              {job.headcount != null && (
                <div>
                  <dt>Số lượng</dt>
                  <dd>{job.headcount} người</dd>
                </div>
              )}
              <div>
                <dt>Cơ sở nhận hồ sơ</dt>
                <dd>{storesText(job.chainWide, job.stores) || 'Chưa có cơ sở nhận hồ sơ'}</dd>
              </div>
              <div>
                <dt>Hạn nộp</dt>
                <dd>{deadlineText(job.deadline)}</dd>
              </div>
            </dl>
          </div>
        </aside>
      </div>

      <section className="card" id="apply">
        <div className="card__head">
          <h2 className="card__title">Ứng tuyển vị trí này</h2>
        </div>
        <div className="card__body">
          {job.acceptingApplications && job.stores.length > 0 ? (
            <ApplyForm job={job} />
          ) : (
            <div className="alert-banner alert-error" role="status">
              <XCircle size={18} />
              <span>Tin tuyển dụng đã hết hạn nhận hồ sơ</span>
            </div>
          )}
        </div>
      </section>
    </div>
  );
};
```

- [ ] **Step 4: Route trong `frontend/src/App.tsx`**

Thêm import:

```tsx
import { JobsPage } from './pages/careers/JobsPage';
import { JobDetailPage } from './pages/careers/JobDetailPage';
```

Ngay sau hai route `tin-tuc` (Task 11) thêm:

```tsx
                <Route path="tuyen-dung" element={<JobsPage />} />
                <Route path="tuyen-dung/:slug" element={<JobDetailPage />} />
```

- [ ] **Step 5: Link Tuyển dụng (`CustomerLayout.tsx`)**

Header — sửa

```tsx
            <NavLink to="/tin-tuc" className={navLinkClass}>
              Tin tức
            </NavLink>
```

thành

```tsx
            <NavLink to="/tin-tuc" className={navLinkClass}>
              Tin tức
            </NavLink>
            <NavLink to="/tuyen-dung" className={navLinkClass}>
              Tuyển dụng
            </NavLink>
```

Footer — sửa `<Link to="/tin-tuc">Tin tức</Link>` thành

```tsx
            <Link to="/tin-tuc">Tin tức</Link>
            <Link to="/tuyen-dung">Tuyển dụng</Link>
```

- [ ] **Step 6: Cổng kiểm + bấm thử**

Run (trong `frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → 26 cảnh báo.

Bấm thử (ADMIN tạo bằng Swagger 1 tin mở gắn 1 cơ sở, 1 tin toàn chuỗi, 1 tin đã đóng):
- `/tuyen-dung` liệt kê 2 tin đang mở; chọn cơ sở khác → chỉ còn tin toàn chuỗi.
- `/tuyen-dung/<tin-mở>`: mô tả Markdown, khối thông tin, form; bỏ trống họ tên/SĐT → báo lỗi tại ô; chọn file `.docx` → "File CV phải là PDF, JPG hoặc PNG".
- Nộp hợp lệ kèm PDF → khung xanh "Đã gửi hồ sơ ứng tuyển…"; nộp lại cùng SĐT → toast "Bạn đã nộp hồ sơ cho vị trí này".
- `/tuyen-dung/<tin-đã-đóng>` → không có form, hiện "Tin tuyển dụng đã hết hạn nhận hồ sơ".

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add frontend/src/components/careers/ApplyForm.tsx frontend/src/pages/careers frontend/src/App.tsx frontend/src/components/layout/CustomerLayout.tsx
git commit -m "feat(frontend): trang tuyển dụng lọc theo cơ sở, chi tiết tin và form ứng tuyển kèm CV"
```

---

## Task 13: Frontend — form phản hồi ở trang Liên hệ + nút "Phản hồi về đơn này" ở trang theo dõi đơn

**Files:**
- Create (gốc `frontend/src/`): `components/feedback/FeedbackForm.tsx`
- Modify: `pages/static/StaticPages.tsx` (`ContactPage` gắn form), `pages/OrderTrackingPage.tsx` (nút sang `/contact?orderCode=…`)

**Interfaces:**
- Consumes: `feedbackApi.submit`, `FeedbackType`, `FEEDBACK_TYPE_LABEL`, `VN_PHONE_REGEX`, `EMAIL_REGEX`, `normalizePhone` (Task 10); `orderApi.getUserOrders(page, size)`, `orderApi.getOrderByCode(code)`, `OrderResponse` (`storeId`, `storeName`, `orderCode`, `createdAt`, `total`); `storeApi.listPublic()`; `useAuth` (`user.fullName/phone/email`, `isAuthenticated`).
- Produces: `<FeedbackForm />` (đọc `?orderCode=` từ URL; khối có `id="feedback"`), nút **Phản hồi về đơn này**.

- [ ] **Step 1: `frontend/src/components/feedback/FeedbackForm.tsx`**

```tsx
import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { MessageSquare, Send } from 'lucide-react';
import { feedbackApi } from '../../api/feedbackApi';
import { orderApi } from '../../api/orderApi';
import { storeApi } from '../../api/storeApi';
import { useAuth } from '../../context/useAuth';
import { Button, Input, Select, Textarea, useToast } from '../ui';
import { EMAIL_REGEX, FEEDBACK_TYPE_LABEL, VN_PHONE_REGEX, normalizePhone } from '../../utils/contentLabels';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import type { FeedbackType } from '../../types/content';
import type { OrderResponse } from '../../types/order';
import type { PublicStore } from '../../types/store';
import '../../styles/components/content.css';

const FEEDBACK_TYPES: FeedbackType[] = ['SUGGESTION', 'COMPLAINT', 'PARTNERSHIP', 'OTHER'];
const RECENT_ORDERS = 20;

interface FormState {
  type: FeedbackType;
  storeId: number | null;
  orderCode: string;
  fullName: string;
  phone: string;
  email: string;
  subject: string;
  content: string;
  website: string;
}

type FormErrors = Partial<Record<keyof FormState, string>>;

const initialState = (orderCode: string): FormState => ({
  type: orderCode ? 'COMPLAINT' : 'SUGGESTION',
  storeId: null,
  orderCode,
  fullName: '',
  phone: '',
  email: '',
  subject: orderCode ? `Phản hồi về đơn ${orderCode}` : '',
  content: '',
  website: '',
});

/**
 * Form phản hồi (spec D §5). Khách đăng nhập chọn được đơn của mình → cơ sở tự điền theo đơn và bị khoá.
 * `?orderCode=` (từ trang theo dõi đơn) mở sẵn đơn đó.
 */
export const FeedbackForm = () => {
  const { user, isAuthenticated } = useAuth();
  const toast = useToast();
  const [searchParams] = useSearchParams();
  const initialOrderCode = searchParams.get('orderCode')?.trim() ?? '';
  const sectionRef = useRef<HTMLElement>(null);

  const [stores, setStores] = useState<PublicStore[]>([]);
  const [orders, setOrders] = useState<OrderResponse[]>([]);
  const [ordersLoaded, setOrdersLoaded] = useState(false);
  const [form, setForm] = useState<FormState>(() => initialState(initialOrderCode));
  const [errors, setErrors] = useState<FormErrors>({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [doneMessage, setDoneMessage] = useState<string | null>(null);

  useEffect(() => {
    if (initialOrderCode) sectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [initialOrderCode]);

  useEffect(() => {
    let alive = true;
    storeApi
      .listPublic()
      .then((data) => {
        if (alive) setStores(data);
      })
      .catch(() => {
        if (alive) setStores([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  // Điền sẵn từ tài khoản — chỉ lấp ô còn trống, người dùng sửa được
  useEffect(() => {
    if (!user) return;
    setForm((prev) => ({
      ...prev,
      fullName: prev.fullName || user.fullName || '',
      phone: prev.phone || user.phone || '',
      email: prev.email || user.email || '',
    }));
  }, [user]);

  // Đơn gần đây của khách (API đơn hàng hiện có) + đơn trong URL nếu nằm ngoài trang đầu
  useEffect(() => {
    if (!isAuthenticated) return;
    let alive = true;
    const load = async () => {
      try {
        const page = await orderApi.getUserOrders(0, RECENT_ORDERS);
        let list = page.content;
        if (initialOrderCode && !list.some((order) => order.orderCode === initialOrderCode)) {
          try {
            list = [await orderApi.getOrderByCode(initialOrderCode), ...list];
          } catch {
            // Không phải đơn của mình → bỏ qua, phản hồi gửi không kèm đơn
          }
        }
        if (alive) setOrders(list);
      } catch {
        if (alive) setOrders([]);
      } finally {
        if (alive) setOrdersLoaded(true);
      }
    };
    void load();
    return () => {
      alive = false;
    };
  }, [isAuthenticated, initialOrderCode]);

  const selectedOrder = orders.find((order) => order.orderCode === form.orderCode) ?? null;
  const storeValue = selectedOrder?.storeId ?? form.storeId;
  const orderNotFound = isAuthenticated && ordersLoaded && form.orderCode !== '' && selectedOrder === null;

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((prev) => ({ ...prev, [key]: value }));

  const validate = (): boolean => {
    const next: FormErrors = {};
    if (!form.fullName.trim()) next.fullName = 'Vui lòng nhập họ tên';
    const phone = normalizePhone(form.phone);
    const email = form.email.trim();
    if (!phone && !email) next.phone = 'Vui lòng nhập số điện thoại hoặc email để cửa hàng liên hệ lại';
    if (phone && !VN_PHONE_REGEX.test(phone)) next.phone = 'Số điện thoại không đúng định dạng Việt Nam';
    if (email && !EMAIL_REGEX.test(email)) next.email = 'Email không đúng định dạng';
    if (!form.subject.trim()) next.subject = 'Vui lòng nhập tiêu đề';
    const contentLength = form.content.trim().length;
    if (contentLength < 10 || contentLength > 5000) next.content = 'Nội dung phải từ 10 đến 5000 ký tự';
    setErrors(next);
    return Object.keys(next).length === 0;
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (!validate()) return;
    setIsSubmitting(true);
    try {
      const message = await feedbackApi.submit({
        type: form.type,
        storeId: selectedOrder ? null : form.storeId,
        orderCode: selectedOrder ? selectedOrder.orderCode : null,
        fullName: form.fullName.trim(),
        phone: normalizePhone(form.phone) || null,
        email: form.email.trim() || null,
        subject: form.subject.trim(),
        content: form.content.trim(),
        website: form.website,
      });
      setDoneMessage(message);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Gửi phản hồi thất bại, vui lòng thử lại');
    } finally {
      setIsSubmitting(false);
    }
  };

  const resetForm = () => {
    setDoneMessage(null);
    setErrors({});
    setForm({
      ...initialState(''),
      fullName: user?.fullName ?? '',
      phone: user?.phone ?? '',
      email: user?.email ?? '',
    });
  };

  return (
    <section ref={sectionRef} id="feedback">
      <h2>
        <MessageSquare size={20} /> Gửi phản hồi cho cửa hàng
      </h2>

      {doneMessage ? (
        <div className="cf-form__success" role="status">
          <strong>{doneMessage}</strong>
          <span>Cửa hàng sẽ xem và liên hệ lại với bạn nếu cần.</span>
          <Button size="sm" variant="secondary" onClick={resetForm}>
            Gửi phản hồi khác
          </Button>
        </div>
      ) : (
        <form className="cf-form" onSubmit={(event) => void handleSubmit(event)} noValidate>
          <div className="cf-form__row">
            <Select label="Loại phản hồi" required value={form.type} onChange={(event) => setField('type', event.target.value as FeedbackType)}>
              {FEEDBACK_TYPES.map((type) => (
                <option key={type} value={type}>
                  {FEEDBACK_TYPE_LABEL[type]}
                </option>
              ))}
            </Select>
            <Select
              label="Cơ sở"
              value={storeValue == null ? '' : String(storeValue)}
              disabled={selectedOrder !== null}
              hint={selectedOrder ? 'Tự lấy theo cơ sở phục vụ đơn hàng' : undefined}
              onChange={(event) => setField('storeId', event.target.value ? Number(event.target.value) : null)}
            >
              <option value="">Chung toàn chuỗi</option>
              {selectedOrder?.storeId != null && !stores.some((store) => store.id === selectedOrder.storeId) && (
                <option value={selectedOrder.storeId}>{selectedOrder.storeName ?? 'Cơ sở của đơn'}</option>
              )}
              {stores.map((store) => (
                <option key={store.id} value={store.id}>
                  {store.name}
                </option>
              ))}
            </Select>
          </div>

          {isAuthenticated ? (
            <Select
              label="Đơn hàng liên quan"
              value={selectedOrder ? selectedOrder.orderCode : ''}
              onChange={(event) => setField('orderCode', event.target.value)}
              hint={orderNotFound ? `Không tìm thấy đơn ${form.orderCode} trong tài khoản của bạn — phản hồi sẽ gửi không kèm đơn.` : undefined}
            >
              <option value="">Không gắn đơn hàng</option>
              {orders.map((order) => (
                <option key={order.orderCode} value={order.orderCode}>
                  {order.orderCode} · {formatDateTime(order.createdAt)} · {formatCurrency(order.total)}
                </option>
              ))}
            </Select>
          ) : (
            form.orderCode && (
              <p className="cf-form__note">
                <Link to="/login">Đăng nhập</Link> để gắn phản hồi với đơn {form.orderCode}.
              </p>
            )
          )}

          <div className="cf-form__row">
            <Input label="Họ và tên" required maxLength={100} value={form.fullName} onChange={(event) => setField('fullName', event.target.value)} error={errors.fullName} />
            <Input label="Số điện thoại" type="tel" maxLength={20} value={form.phone} onChange={(event) => setField('phone', event.target.value)} error={errors.phone} />
          </div>
          <Input label="Email" type="email" maxLength={150} value={form.email} onChange={(event) => setField('email', event.target.value)} error={errors.email} hint="Cần ít nhất số điện thoại hoặc email." />
          <Input label="Tiêu đề" required maxLength={200} value={form.subject} onChange={(event) => setField('subject', event.target.value)} error={errors.subject} />
          <Textarea
            label="Nội dung"
            required
            rows={6}
            maxLength={5000}
            value={form.content}
            onChange={(event) => setField('content', event.target.value)}
            error={errors.content}
            hint={`${form.content.trim().length}/5000 ký tự (tối thiểu 10)`}
          />

          {/* Ô bẫy bot — người dùng không thấy */}
          <div className="hp-field" aria-hidden="true">
            <label>
              Website
              <input type="text" name="website" tabIndex={-1} autoComplete="off" value={form.website} onChange={(event) => setField('website', event.target.value)} />
            </label>
          </div>

          <Button type="submit" loading={isSubmitting} icon={<Send size={16} />}>
            Gửi phản hồi
          </Button>
        </form>
      )}
    </section>
  );
};
```

- [ ] **Step 2: `ContactPage` gắn form (`frontend/src/pages/static/StaticPages.tsx`)**

Thêm import: `import { FeedbackForm } from '../../components/feedback/FeedbackForm';`

Trong `ContactPage`, sửa đoạn cuối:

```tsx
      <h2>Cần trao đổi về một đơn cụ thể?</h2>
      <p>
        Mở <Link to="/orders">Đơn của tôi</Link>, chọn đơn đang giao và đọc trạng thái mới nhất trước khi gọi —
        cửa hàng sẽ hỏi mã đơn (dạng <strong>BMK-…</strong>) để tra nhanh hơn.
      </p>
    </StaticPage>
```

thành:

```tsx
      <h2>Cần trao đổi về một đơn cụ thể?</h2>
      <p>
        Mở <Link to="/orders">Đơn của tôi</Link>, chọn đơn rồi bấm <strong>Phản hồi về đơn này</strong> — form bên
        dưới sẽ tự gắn đơn và cơ sở phục vụ. Việc gấp vẫn nên gọi hotline của cửa hàng.
      </p>

      <FeedbackForm />
    </StaticPage>
```

Trong `ContactPage`, sửa `subtitle="Gọi điện cho cửa hàng khi cần gấp, hoặc gửi email cho các việc khác."` thành `subtitle="Gọi điện khi cần gấp, hoặc gửi phản hồi để cửa hàng xử lý."`.

- [ ] **Step 3: Nút ở trang theo dõi đơn (`frontend/src/pages/OrderTrackingPage.tsx`)**

Đổi import lucide thành:

```tsx
import { AlertTriangle, Check, Info, MessageSquare, Receipt, SearchX, Star, Truck, XCircle } from 'lucide-react';
```

Trong `<div className="track__head">` của `page-bar`, sửa

```tsx
          <Button icon={<Receipt size={17} />} onClick={() => navigate('/menu')}>
            Đặt món mới
          </Button>
```

thành

```tsx
          <Link className="ui-btn ui-btn--ghost ui-btn--md" to={`/contact?orderCode=${encodeURIComponent(order.orderCode)}`}>
            <MessageSquare size={17} />
            Phản hồi về đơn này
          </Link>
          <Button icon={<Receipt size={17} />} onClick={() => navigate('/menu')}>
            Đặt món mới
          </Button>
```

- [ ] **Step 4: Cổng kiểm + bấm thử**

Run (trong `frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → 26 cảnh báo.

Bấm thử:
- Chưa đăng nhập, `/contact`: form không có ô đơn hàng; gửi thiếu SĐT và email → lỗi tại ô SĐT; gửi hợp lệ → khung xanh "Cảm ơn bạn! Phản hồi đã được gửi tới cửa hàng.".
- Đăng nhập khách, `/orders/<mã>` → bấm **Phản hồi về đơn này** → `/contact?orderCode=<mã>`, trang cuộn tới form, đơn đã chọn, ô cơ sở bị khoá đúng cơ sở của đơn, loại = Khiếu nại, họ tên/SĐT/email điền sẵn.
- Sửa URL thành `?orderCode=<mã-của-khách-khác>` → gợi ý "Không tìm thấy đơn … phản hồi sẽ gửi không kèm đơn".
- Gửi 6 phản hồi liên tiếp → lần 6 toast "Bạn thao tác quá nhanh, vui lòng thử lại sau".

- [ ] **Step 5: Commit (người dùng tự chạy)**

```
git add frontend/src/components/feedback/FeedbackForm.tsx frontend/src/pages/static/StaticPages.tsx frontend/src/pages/OrderTrackingPage.tsx
git commit -m "feat(frontend): form phản hồi ở trang Liên hệ (gắn đơn của mình, cơ sở theo đơn) và nút Phản hồi về đơn này"
```

---

## Task 14: Frontend — ADMIN quản lý Tin tức (`/admin/news`)

**Files:**
- Create (gốc `frontend/src/`): `pages/admin/AdminNewsPage.tsx`
- Modify: `App.tsx` (route `/admin/news`), `components/layout/navItems.ts` (mục **Tin tức** trong `ADMIN_NAV`)

**Interfaces:**
- Consumes: `newsApi.adminList/adminGet/adminCreate/adminUpdate/adminDelete`, `AdminNews`, `NewsPayload`, `NewsDisplayState`, `NEWS_STATE_LABEL`, `NEWS_STATE_TONE`, `MarkdownEditor` (Task 10); `staffCatalogApi.uploadImage(file)` (đã có, ADMIN); `toDateTimeLocal` (`utils/pricing.ts`, đã có); `formatDateTime`; UI `Badge`, `Button`, `ChipGroup`, `EmptyState`, `Input`, `Modal`, `PageHeader`, `Pagination`, `Select`, `Skeleton`, `Textarea`, `useConfirm`, `useToast`.
- Produces: `AdminNewsPage`; mục menu ADMIN `{ to: '/admin/news', label: 'Tin tức', icon: Newspaper }`.

- [ ] **Step 1: `frontend/src/pages/admin/AdminNewsPage.tsx`**

```tsx
import { useCallback, useEffect, useRef, useState } from 'react';
import type { ChangeEvent } from 'react';
import { ExternalLink, Newspaper, Pencil, Pin, Plus, Trash2, Upload } from 'lucide-react';
import { newsApi } from '../../api/newsApi';
import { staffCatalogApi } from '../../api/staffCatalogApi';
import {
  Badge,
  Button,
  ChipGroup,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Textarea,
  useConfirm,
  useToast,
} from '../../components/ui';
import { MarkdownEditor } from '../../components/content/MarkdownEditor';
import { NEWS_STATE_LABEL, NEWS_STATE_TONE } from '../../utils/contentLabels';
import { formatDateTime } from '../../utils/formatters';
import { toDateTimeLocal } from '../../utils/pricing';
import type { AdminNews, NewsDisplayState, NewsPayload, NewsStatus } from '../../types/content';
import '../../styles/components/table.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 20;
const MAX_IMAGE_MB = 5;

type StateFilter = NewsDisplayState | 'ALL';

const FILTERS: { value: StateFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'DRAFT', label: 'Nháp' },
  { value: 'SCHEDULED', label: 'Hẹn giờ' },
  { value: 'PUBLISHED', label: 'Đã đăng' },
];

const EMPTY_FORM: NewsPayload = {
  title: '',
  slug: '',
  coverImageUrl: '',
  summary: '',
  content: '',
  status: 'DRAFT',
  publishedAt: null,
  pinned: false,
};

const toForm = (news: AdminNews): NewsPayload => ({
  title: news.title,
  slug: news.slug,
  coverImageUrl: news.coverImageUrl ?? '',
  summary: news.summary ?? '',
  content: news.content ?? '',
  status: news.status,
  publishedAt: toDateTimeLocal(news.publishedAt) || null,
  pinned: news.pinned,
});

/** Ảnh bìa + ảnh chèn trong bài dùng chung API tải ảnh của thực đơn (ADMIN) */
const uploadImage = async (file: File): Promise<string> => {
  if (!file.type.startsWith('image/')) throw new Error('Tệp tải lên phải là ảnh (PNG, JPG, WEBP, GIF).');
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) throw new Error(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
  return staffCatalogApi.uploadImage(file);
};

/** ADMIN — soạn tin tức: lọc Nháp / Hẹn giờ / Đã đăng, form Markdown có xem trước. */
export const AdminNewsPage = () => {
  const toast = useToast();
  const confirm = useConfirm();
  const coverInputRef = useRef<HTMLInputElement>(null);
  const [filter, setFilter] = useState<StateFilter>('ALL');
  const [keyword, setKeyword] = useState('');
  const [appliedKeyword, setAppliedKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<AdminNews[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [editing, setEditing] = useState<{ id: number | null; form: NewsPayload } | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploadingCover, setIsUploadingCover] = useState(false);

  const load = useCallback(async () => {
    try {
      const data = await newsApi.adminList({
        status: filter === 'ALL' ? undefined : filter,
        keyword: appliedKeyword || undefined,
        page: page - 1,
        size: PAGE_SIZE,
      });
      setItems(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được danh sách bài viết');
    } finally {
      setIsLoading(false);
    }
  }, [filter, appliedKeyword, page, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  // Gõ tới đâu lọc tới đó — debounce 300ms
  useEffect(() => {
    const timer = window.setTimeout(() => {
      setAppliedKeyword(keyword.trim());
      setPage(1);
    }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  const setField = <K extends keyof NewsPayload>(key: K, value: NewsPayload[K]) =>
    setEditing((prev) => (prev ? { ...prev, form: { ...prev.form, [key]: value } } : prev));

  const openEdit = async (news: AdminNews) => {
    try {
      const full = await newsApi.adminGet(news.id);
      setEditing({ id: full.id, form: toForm(full) });
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được bài viết');
    }
  };

  const handleCover = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    setIsUploadingCover(true);
    try {
      setField('coverImageUrl', await uploadImage(file));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Tải ảnh bìa thất bại');
    } finally {
      setIsUploadingCover(false);
    }
  };

  const handleSave = async () => {
    if (!editing) return;
    const { id, form } = editing;
    const title = form.title.trim();
    if (title.length < 3 || title.length > 200) {
      toast.error('Tiêu đề phải từ 3 đến 200 ký tự');
      return;
    }
    if (!form.content.trim()) {
      toast.error('Nội dung bài viết không được để trống');
      return;
    }
    const payload: NewsPayload = {
      ...form,
      title,
      slug: form.slug?.trim() || null,
      coverImageUrl: form.coverImageUrl?.trim() || null,
      summary: form.summary?.trim() || null,
      publishedAt: form.publishedAt || null,
    };
    setIsSaving(true);
    try {
      if (id) {
        await newsApi.adminUpdate(id, payload);
      } else {
        await newsApi.adminCreate(payload);
      }
      toast.success('Đã lưu bài viết');
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lưu bài viết thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (news: AdminNews) => {
    const accepted = await confirm({
      title: 'Xoá bài viết',
      message: `Xoá "${news.title}"? Bài sẽ biến mất khỏi trang khách.`,
      confirmText: 'Xoá',
      danger: true,
    });
    if (!accepted) return;
    try {
      await newsApi.adminDelete(news.id);
      toast.success('Đã xoá bài viết');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá bài viết thất bại');
    }
  };

  const form = editing?.form;

  return (
    <>
      <PageHeader
        title="Tin tức"
        subtitle="Bài tin tức và khuyến mãi hiển thị ở trang chủ và mục Tin tức của khách."
        actions={
          <Button icon={<Plus size={17} />} onClick={() => setEditing({ id: null, form: EMPTY_FORM })}>
            Viết bài mới
          </Button>
        }
      />

      <div className="inbox-filters">
        <ChipGroup<StateFilter>
          options={FILTERS}
          value={filter}
          onChange={(value) => {
            setFilter(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái bài"
        />
        <Input label="Tìm theo tiêu đề" value={keyword} onChange={(event) => setKeyword(event.target.value)} />
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : items.length === 0 ? (
        <EmptyState icon={<Newspaper size={30} />} title="Chưa có bài viết" description="Bấm Viết bài mới để bắt đầu." />
      ) : (
        <section className="card">
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Tiêu đề</th>
                  <th>Trạng thái</th>
                  <th>Thời điểm đăng</th>
                  <th>Người soạn</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((news) => (
                  <tr key={news.id}>
                    <td>
                      <span className="ui-table__primary">
                        {news.pinned && <Pin size={13} aria-label="Đang ghim" />} {news.title}
                      </span>
                      <span className="ui-table__meta">/{news.slug}</span>
                    </td>
                    <td>
                      <Badge tone={NEWS_STATE_TONE[news.displayState]}>{NEWS_STATE_LABEL[news.displayState]}</Badge>
                    </td>
                    <td>{news.publishedAt ? formatDateTime(news.publishedAt) : '—'}</td>
                    <td>{news.authorName ?? '—'}</td>
                    <td>
                      <div className="ui-table__actions">
                        {news.displayState === 'PUBLISHED' && (
                          <a className="ui-btn ui-btn--ghost ui-btn--sm" href={`/tin-tuc/${news.slug}`} target="_blank" rel="noopener noreferrer">
                            <ExternalLink size={15} />
                            Xem
                          </a>
                        )}
                        <Button size="sm" variant="ghost" icon={<Pencil size={15} />} onClick={() => void openEdit(news)}>
                          Sửa
                        </Button>
                        <Button size="sm" variant="ghost" icon={<Trash2 size={15} />} onClick={() => void handleDelete(news)}>
                          Xoá
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </section>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        size="xl"
        title={editing?.id ? 'Sửa bài viết' : 'Viết bài mới'}
        closeOnBackdrop={false}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>
              Huỷ
            </Button>
            <Button loading={isSaving} onClick={() => void handleSave()}>
              Lưu bài viết
            </Button>
          </>
        }
      >
        {form && (
          <div className="cf-form">
            <Input label="Tiêu đề" required maxLength={200} value={form.title} onChange={(event) => setField('title', event.target.value)} />
            <Input
              label="Slug (đường dẫn)"
              maxLength={200}
              value={form.slug ?? ''}
              onChange={(event) => setField('slug', event.target.value)}
              hint="Bỏ trống để tự sinh từ tiêu đề (bỏ dấu). Trùng sẽ tự thêm -2, -3…"
            />
            <div className="cf-form__row">
              <Input
                label="Ảnh bìa (URL)"
                maxLength={500}
                value={form.coverImageUrl ?? ''}
                onChange={(event) => setField('coverImageUrl', event.target.value)}
              />
              <div className="ui-field">
                <span className="ui-field__label">Tải ảnh bìa</span>
                <Button variant="secondary" icon={<Upload size={16} />} loading={isUploadingCover} onClick={() => coverInputRef.current?.click()}>
                  Chọn ảnh
                </Button>
                <input ref={coverInputRef} type="file" accept="image/*" hidden onChange={(event) => void handleCover(event)} />
              </div>
            </div>
            {form.coverImageUrl && <img className="news-article__cover" src={form.coverImageUrl} alt="Ảnh bìa" />}
            <Textarea label="Tóm tắt (hiện trên thẻ bài)" rows={2} maxLength={500} value={form.summary ?? ''} onChange={(event) => setField('summary', event.target.value)} />
            <MarkdownEditor label="Nội dung" value={form.content} onChange={(value) => setField('content', value)} onUploadImage={uploadImage} />
            <div className="cf-form__row">
              <Select label="Trạng thái" value={form.status} onChange={(event) => setField('status', event.target.value as NewsStatus)}>
                <option value="DRAFT">Nháp</option>
                <option value="PUBLISHED">Đăng</option>
              </Select>
              <Input
                label="Thời điểm đăng"
                type="datetime-local"
                value={form.publishedAt ?? ''}
                onChange={(event) => setField('publishedAt', event.target.value || null)}
                hint="Giờ Việt Nam. Bỏ trống khi Đăng = đăng ngay; thời điểm tương lai = hẹn giờ."
              />
            </div>
            <label className="ui-check">
              <input type="checkbox" checked={form.pinned} onChange={(event) => setField('pinned', event.target.checked)} />
              Ghim lên đầu danh sách tin
            </label>
          </div>
        )}
      </Modal>
    </>
  );
};
```

- [ ] **Step 2: Route + menu**

`frontend/src/App.tsx`: thêm `import { AdminNewsPage } from './pages/admin/AdminNewsPage';` và trong khối `/admin`, sau `<Route path="promotions" element={<AdminPromotionsPage />} />` thêm:

```tsx
                  <Route path="news" element={<AdminNewsPage />} />
```

`frontend/src/components/layout/navItems.ts`: thêm `Newspaper` vào import từ `lucide-react` (giữ thứ tự chữ cái: sau `LineChart`), và trong `ADMIN_NAV` sau dòng `Mã giảm giá` thêm:

```ts
  { to: '/admin/news', label: 'Tin tức', icon: Newspaper },
```

- [ ] **Step 3: Cổng kiểm + bấm thử**

Run (trong `frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → 26 cảnh báo.

Bấm thử (đăng nhập `admin@gmail.com`):
- Menu **Tin tức** → bảng; **Viết bài mới**: tiêu đề "Khuyến mãi tháng 10 — Đặc biệt", bỏ trống slug, dùng nút **B**/**I**/H2/danh sách/link/**Chèn ảnh** (tải ảnh thật), tab **Xem trước** hiển thị đúng; lưu ở trạng thái Nháp → badge **Nháp**, slug `/khuyen-mai-thang-10-dac-biet`.
- Sửa → Đăng, thời điểm đăng = ngày mai 08:00 → badge **Hẹn giờ**; khách mở `/tin-tuc/<slug>` → "Không tìm thấy bài viết".
- Sửa → Đăng, bỏ trống thời điểm → badge **Đã đăng**, cột thời điểm = lúc lưu; nút **Xem** mở tab bài.
- Ghim → bài lên đầu `/tin-tuc`; chip lọc Nháp/Hẹn giờ/Đã đăng đúng; Xoá → bài biến mất ở cả hai phía.

- [ ] **Step 4: Commit (người dùng tự chạy)**

```
git add frontend/src/pages/admin/AdminNewsPage.tsx frontend/src/App.tsx frontend/src/components/layout/navItems.ts
git commit -m "feat(frontend): trang quản lý tin tức cho ADMIN — lọc nháp/hẹn giờ/đã đăng, soạn Markdown có xem trước, ảnh bìa, ghim"
```

---

## Task 15: Frontend — ADMIN Tuyển dụng (tab Tin tuyển dụng + tab Hồ sơ) và MANAGER Hồ sơ ứng tuyển

**Files:**
- Create (gốc `frontend/src/`): `components/careers/JobApplicationsPanel.tsx`, `pages/admin/AdminJobsPage.tsx`, `pages/manager/ManagerApplicationsPage.tsx`
- Modify: `App.tsx` (route `/admin/jobs`, `/staff/applications`), `components/layout/navItems.ts` (ADMIN **Tuyển dụng**, MANAGER **Hồ sơ ứng tuyển**)

**Interfaces:**
- Consumes: `jobApi.adminList/adminCreate/adminUpdate/adminSetStatus/adminDelete/listOpen/listApplications/updateApplication/downloadCv`, `AdminJob`, `JobPayload`, `JobStatus`, `EmploymentType`, `JobApplication`, `ApplicationStatus`, `EMPLOYMENT_TYPE_LABEL`, `JOB_STATUS_LABEL`, `APPLICATION_STATUS_LABEL`, `APPLICATION_STATUS_TONE`, `storesText`, `deadlineText`, `notifyInboxChanged`, `MarkdownEditor` (Task 10); `storeApi.adminList()`, `Store`; `StoreScopeSelect`; `staffCatalogApi.uploadImage`; UI `Tabs`, `ChipGroup`, `Modal`, `Badge`, …
- Produces: `<JobApplicationsPanel isAdmin={boolean} />` (bảng + ngăn chi tiết, tải CV, đổi trạng thái, ghi chú nội bộ; gọi `notifyInboxChanged()` sau khi lưu), `AdminJobsPage` (`/admin/jobs`), `ManagerApplicationsPage` (`/staff/applications`, chỉ MANAGER).

- [ ] **Step 1: `frontend/src/components/careers/JobApplicationsPanel.tsx`**

```tsx
import { useCallback, useEffect, useState } from 'react';
import { Download, FileText, Inbox, Phone } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { Badge, Button, ChipGroup, EmptyState, Pagination, Select, Skeleton, Textarea, useToast } from '../ui';
import { StoreScopeSelect } from '../store/StoreScopeSelect';
import { APPLICATION_STATUS_LABEL, APPLICATION_STATUS_TONE } from '../../utils/contentLabels';
import { formatDateTime } from '../../utils/formatters';
import { notifyInboxChanged } from '../../utils/inboxEvents';
import type { ApplicationStatus, JobApplication } from '../../types/content';
import '../../styles/components/table.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 20;
const STATUSES: ApplicationStatus[] = ['NEW', 'CONTACTED', 'HIRED', 'REJECTED'];

type StatusFilter = ApplicationStatus | 'ALL';

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  ...STATUSES.map((status) => ({ value: status, label: APPLICATION_STATUS_LABEL[status] })),
];

export interface JobApplicationsPanelProps {
  /** ADMIN: lọc mọi cơ sở + lấy danh sách tin từ /admin/jobs; MANAGER: backend tự khoá cơ sở mình */
  isAdmin: boolean;
}

/** Màn hồ sơ ứng tuyển dùng chung cho ADMIN (tab Hồ sơ) và MANAGER (menu Hồ sơ ứng tuyển). */
export const JobApplicationsPanel = ({ isAdmin }: JobApplicationsPanelProps) => {
  const toast = useToast();
  const [jobs, setJobs] = useState<{ id: number; title: string }[]>([]);
  const [jobId, setJobId] = useState<number | null>(null);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<JobApplication[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [selected, setSelected] = useState<JobApplication | null>(null);
  const [draftStatus, setDraftStatus] = useState<ApplicationStatus>('NEW');
  const [draftNote, setDraftNote] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    const request = isAdmin
      ? jobApi.adminList({ size: 50 }).then((page) => page.content.map((job) => ({ id: job.id, title: job.title })))
      : jobApi.listOpen().then((list) => list.map((job) => ({ id: job.id, title: job.title })));
    request
      .then((list) => {
        if (alive) setJobs(list);
      })
      .catch(() => {
        if (alive) setJobs([]);
      });
    return () => {
      alive = false;
    };
  }, [isAdmin]);

  const load = useCallback(async () => {
    try {
      const data = await jobApi.listApplications({
        jobId: jobId ?? undefined,
        storeId: isAdmin ? storeId ?? undefined : undefined,
        status: status === 'ALL' ? undefined : status,
        page: page - 1,
        size: PAGE_SIZE,
      });
      setItems(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được hồ sơ ứng tuyển');
    } finally {
      setIsLoading(false);
    }
  }, [jobId, storeId, status, page, isAdmin, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const select = (application: JobApplication) => {
    setSelected(application);
    setDraftStatus(application.status);
    setDraftNote(application.internalNote ?? '');
  };

  const handleSave = async () => {
    if (!selected) return;
    setIsSaving(true);
    try {
      const updated = await jobApi.updateApplication(selected.id, { status: draftStatus, internalNote: draftNote });
      toast.success('Đã cập nhật hồ sơ');
      select(updated);
      notifyInboxChanged();
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật hồ sơ thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const handleDownload = async (application: JobApplication) => {
    try {
      await jobApi.downloadCv(application);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được CV');
    }
  };

  return (
    <>
      <div className="inbox-filters">
        <Select
          label="Tin tuyển dụng"
          value={jobId == null ? '' : String(jobId)}
          onChange={(event) => {
            setJobId(event.target.value ? Number(event.target.value) : null);
            setPage(1);
          }}
        >
          <option value="">Tất cả tin</option>
          {jobs.map((job) => (
            <option key={job.id} value={job.id}>
              {job.title}
            </option>
          ))}
        </Select>
        {isAdmin && (
          <StoreScopeSelect
            value={storeId}
            onChange={(id) => {
              setStoreId(id);
              setPage(1);
            }}
          />
        )}
        <ChipGroup<StatusFilter>
          options={STATUS_FILTERS}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái hồ sơ"
        />
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : items.length === 0 ? (
        <EmptyState icon={<Inbox size={30} />} title="Chưa có hồ sơ" description="Hồ sơ ứng viên nộp qua trang Tuyển dụng sẽ hiện ở đây." />
      ) : (
        <div className={`inbox${selected ? '' : ' inbox--single'}`}>
          <section className="card">
            <div className="table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Ứng viên</th>
                    <th>Vị trí</th>
                    <th>Cơ sở</th>
                    <th>Ngày nộp</th>
                    <th>Trạng thái</th>
                    <th>CV</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((application) => (
                    <tr
                      key={application.id}
                      className={`inbox__row${selected?.id === application.id ? ' inbox__row--active' : ''}`}
                      tabIndex={0}
                      onClick={() => select(application)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') select(application);
                      }}
                    >
                      <td>
                        <span className="ui-table__primary">{application.fullName}</span>
                        <span className="ui-table__meta">{application.phone}</span>
                      </td>
                      <td>{application.jobTitle}</td>
                      <td>{application.storeName}</td>
                      <td>{formatDateTime(application.createdAt)}</td>
                      <td>
                        <Badge tone={APPLICATION_STATUS_TONE[application.status]}>
                          {APPLICATION_STATUS_LABEL[application.status]}
                        </Badge>
                      </td>
                      <td>{application.hasCv ? <FileText size={16} aria-label="Có CV" /> : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </section>

          {selected && (
            <aside className="card inbox__detail">
              <div className="card__body cf-form">
                <h3 className="card__title">{selected.fullName}</h3>
                <dl>
                  <dt>Vị trí</dt>
                  <dd>{selected.jobTitle}</dd>
                  <dt>Cơ sở</dt>
                  <dd>{selected.storeName}</dd>
                  <dt>Điện thoại</dt>
                  <dd>
                    <a href={`tel:${selected.phone}`}>{selected.phone}</a>
                  </dd>
                  <dt>Email</dt>
                  <dd>{selected.email ? <a href={`mailto:${selected.email}`}>{selected.email}</a> : '—'}</dd>
                  <dt>Ngày nộp</dt>
                  <dd>{formatDateTime(selected.createdAt)}</dd>
                  <dt>Người xử lý</dt>
                  <dd>
                    {selected.handledByName
                      ? `${selected.handledByName} · ${formatDateTime(selected.handledAt)}`
                      : 'Chưa xử lý'}
                  </dd>
                </dl>
                {selected.message && <div className="inbox__message">{selected.message}</div>}
                <div className="inbox__actions">
                  {selected.hasCv && (
                    <Button variant="secondary" size="sm" icon={<Download size={16} />} onClick={() => void handleDownload(selected)}>
                      Tải CV
                    </Button>
                  )}
                  <a className="ui-btn ui-btn--ghost ui-btn--sm" href={`tel:${selected.phone}`}>
                    <Phone size={15} />
                    Gọi ứng viên
                  </a>
                </div>
                <Select label="Trạng thái" value={draftStatus} onChange={(event) => setDraftStatus(event.target.value as ApplicationStatus)}>
                  {STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {APPLICATION_STATUS_LABEL[value]}
                    </option>
                  ))}
                </Select>
                <Textarea label="Ghi chú nội bộ" rows={4} maxLength={2000} value={draftNote} onChange={(event) => setDraftNote(event.target.value)} />
                <div className="inbox__actions">
                  <Button loading={isSaving} onClick={() => void handleSave()}>
                    Lưu
                  </Button>
                  <Button variant="ghost" onClick={() => setSelected(null)}>
                    Đóng
                  </Button>
                </div>
              </div>
            </aside>
          )}
        </div>
      )}
    </>
  );
};
```

- [ ] **Step 2: `frontend/src/pages/admin/AdminJobsPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from 'react';
import { Briefcase, Lock, Pencil, Plus, Trash2, Unlock } from 'lucide-react';
import { jobApi } from '../../api/jobApi';
import { storeApi } from '../../api/storeApi';
import { staffCatalogApi } from '../../api/staffCatalogApi';
import {
  Badge,
  Button,
  ChipGroup,
  EmptyState,
  Input,
  Modal,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Tabs,
  useConfirm,
  useToast,
} from '../../components/ui';
import { MarkdownEditor } from '../../components/content/MarkdownEditor';
import { JobApplicationsPanel } from '../../components/careers/JobApplicationsPanel';
import { EMPLOYMENT_TYPE_LABEL, JOB_STATUS_LABEL, deadlineText, storesText } from '../../utils/contentLabels';
import type { AdminJob, EmploymentType, JobPayload, JobStatus } from '../../types/content';
import type { Store } from '../../types/store';
import '../../styles/components/table.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 20;
const MAX_IMAGE_MB = 5;
const EMPLOYMENT_TYPES: EmploymentType[] = ['FULL_TIME', 'PART_TIME', 'SEASONAL'];

type JobsTab = 'postings' | 'applications';
type JobFilter = JobStatus | 'ALL';

const JOB_FILTERS: { value: JobFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'OPEN', label: 'Đang mở' },
  { value: 'CLOSED', label: 'Đã đóng' },
];

const EMPTY_JOB: JobPayload = {
  title: '',
  slug: '',
  employmentType: 'PART_TIME',
  salaryText: '',
  headcount: null,
  deadline: null,
  description: '',
  status: 'OPEN',
  storeIds: [],
};

const toJobForm = (job: AdminJob): JobPayload => ({
  title: job.title,
  slug: job.slug,
  employmentType: job.employmentType,
  salaryText: job.salaryText ?? '',
  headcount: job.headcount ?? null,
  deadline: job.deadline ?? null,
  description: job.description,
  status: job.status,
  storeIds: job.stores.map((store) => store.id),
});

const uploadImage = async (file: File): Promise<string> => {
  if (!file.type.startsWith('image/')) throw new Error('Tệp tải lên phải là ảnh (PNG, JPG, WEBP, GIF).');
  if (file.size > MAX_IMAGE_MB * 1024 * 1024) throw new Error(`Dung lượng ảnh không được vượt quá ${MAX_IMAGE_MB}MB.`);
  return staffCatalogApi.uploadImage(file);
};

/** Tab "Tin tuyển dụng": bảng + form (vị trí, hình thức, lương, số lượng, hạn nộp, cơ sở, mô tả, trạng thái). */
const JobPostingsPanel = () => {
  const toast = useToast();
  const confirm = useConfirm();
  const [stores, setStores] = useState<Store[]>([]);
  const [filter, setFilter] = useState<JobFilter>('ALL');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<AdminJob[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [editing, setEditing] = useState<{ id: number | null; form: JobPayload } | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    storeApi
      .adminList()
      .then((data) => {
        if (alive) setStores(data.filter((store) => store.active));
      })
      .catch(() => {
        if (alive) setStores([]);
      });
    return () => {
      alive = false;
    };
  }, []);

  const load = useCallback(async () => {
    try {
      const data = await jobApi.adminList({ status: filter === 'ALL' ? undefined : filter, page: page - 1, size: PAGE_SIZE });
      setItems(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được tin tuyển dụng');
    } finally {
      setIsLoading(false);
    }
  }, [filter, page, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const setField = <K extends keyof JobPayload>(key: K, value: JobPayload[K]) =>
    setEditing((prev) => (prev ? { ...prev, form: { ...prev.form, [key]: value } } : prev));

  const toggleStore = (storeId: number, checked: boolean) =>
    setEditing((prev) => {
      if (!prev) return prev;
      const ids = checked
        ? [...prev.form.storeIds, storeId]
        : prev.form.storeIds.filter((id) => id !== storeId);
      return { ...prev, form: { ...prev.form, storeIds: ids } };
    });

  const handleSave = async () => {
    if (!editing) return;
    const { id, form } = editing;
    const title = form.title.trim();
    if (title.length < 3 || title.length > 200) {
      toast.error('Tên vị trí phải từ 3 đến 200 ký tự');
      return;
    }
    if (form.headcount != null && (form.headcount < 1 || form.headcount > 1000)) {
      toast.error('Số lượng cần tuyển phải từ 1 đến 1000');
      return;
    }
    if (!form.description.trim()) {
      toast.error('Mô tả công việc không được để trống');
      return;
    }
    const payload: JobPayload = {
      ...form,
      title,
      slug: form.slug?.trim() || null,
      salaryText: form.salaryText?.trim() || null,
      deadline: form.deadline || null,
    };
    setIsSaving(true);
    try {
      if (id) {
        await jobApi.adminUpdate(id, payload);
      } else {
        await jobApi.adminCreate(payload);
      }
      toast.success('Đã lưu tin tuyển dụng');
      setEditing(null);
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Lưu tin tuyển dụng thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  const toggleStatus = async (job: AdminJob) => {
    try {
      const updated = await jobApi.adminSetStatus(job.id, job.status === 'OPEN' ? 'CLOSED' : 'OPEN');
      toast.success(updated.status === 'OPEN' ? 'Đã mở lại tin tuyển dụng' : 'Đã đóng tin tuyển dụng');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Đổi trạng thái thất bại');
    }
  };

  const handleDelete = async (job: AdminJob) => {
    const accepted = await confirm({
      title: 'Xoá tin tuyển dụng',
      message: `Xoá "${job.title}"? Hồ sơ đã nộp vẫn được giữ lại.`,
      confirmText: 'Xoá',
      danger: true,
    });
    if (!accepted) return;
    try {
      await jobApi.adminDelete(job.id);
      toast.success('Đã xoá tin tuyển dụng');
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Xoá tin thất bại');
    }
  };

  const form = editing?.form;

  return (
    <>
      <div className="inbox-filters">
        <ChipGroup<JobFilter>
          options={JOB_FILTERS}
          value={filter}
          onChange={(value) => {
            setFilter(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái tin"
        />
        <Button icon={<Plus size={17} />} onClick={() => setEditing({ id: null, form: EMPTY_JOB })}>
          Thêm tin tuyển dụng
        </Button>
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : items.length === 0 ? (
        <EmptyState icon={<Briefcase size={30} />} title="Chưa có tin tuyển dụng" description="Bấm Thêm tin tuyển dụng để bắt đầu." />
      ) : (
        <section className="card">
          <div className="table-wrap">
            <table className="ui-table">
              <thead>
                <tr>
                  <th>Vị trí</th>
                  <th>Hình thức</th>
                  <th>Cơ sở</th>
                  <th>Hạn nộp</th>
                  <th>Trạng thái</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((job) => (
                  <tr key={job.id}>
                    <td>
                      <span className="ui-table__primary">{job.title}</span>
                      <span className="ui-table__meta">/{job.slug}</span>
                    </td>
                    <td>{EMPLOYMENT_TYPE_LABEL[job.employmentType]}</td>
                    <td>{storesText(job.chainWide, job.stores)}</td>
                    <td>
                      {deadlineText(job.deadline)}
                      {job.expired && (
                        <>
                          {' '}
                          <Badge tone="danger">Hết hạn</Badge>
                        </>
                      )}
                    </td>
                    <td>
                      <Badge tone={job.status === 'OPEN' ? 'success' : 'neutral'}>{JOB_STATUS_LABEL[job.status]}</Badge>
                    </td>
                    <td>
                      <div className="ui-table__actions">
                        <Button size="sm" variant="ghost" icon={<Pencil size={15} />} onClick={() => setEditing({ id: job.id, form: toJobForm(job) })}>
                          Sửa
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          icon={job.status === 'OPEN' ? <Lock size={15} /> : <Unlock size={15} />}
                          onClick={() => void toggleStatus(job)}
                        >
                          {job.status === 'OPEN' ? 'Đóng' : 'Mở lại'}
                        </Button>
                        <Button size="sm" variant="ghost" icon={<Trash2 size={15} />} onClick={() => void handleDelete(job)}>
                          Xoá
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={page} totalPages={totalPages} onChange={setPage} />
        </section>
      )}

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        size="xl"
        title={editing?.id ? 'Sửa tin tuyển dụng' : 'Thêm tin tuyển dụng'}
        closeOnBackdrop={false}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>
              Huỷ
            </Button>
            <Button loading={isSaving} onClick={() => void handleSave()}>
              Lưu tin
            </Button>
          </>
        }
      >
        {form && (
          <div className="cf-form">
            <Input label="Vị trí" required maxLength={200} value={form.title} onChange={(event) => setField('title', event.target.value)} />
            <Input
              label="Slug (đường dẫn)"
              maxLength={200}
              value={form.slug ?? ''}
              onChange={(event) => setField('slug', event.target.value)}
              hint="Bỏ trống để tự sinh từ tên vị trí."
            />
            <div className="cf-form__row">
              <Select label="Hình thức" value={form.employmentType} onChange={(event) => setField('employmentType', event.target.value as EmploymentType)}>
                {EMPLOYMENT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {EMPLOYMENT_TYPE_LABEL[type]}
                  </option>
                ))}
              </Select>
              <Input
                label="Mức lương"
                maxLength={100}
                value={form.salaryText ?? ''}
                placeholder="22–25k/giờ hoặc Thoả thuận"
                onChange={(event) => setField('salaryText', event.target.value)}
              />
            </div>
            <div className="cf-form__row">
              <Input
                label="Số lượng cần tuyển"
                type="number"
                min={1}
                max={1000}
                value={form.headcount ?? ''}
                onChange={(event) => setField('headcount', event.target.value ? Number(event.target.value) : null)}
              />
              <Input
                label="Hạn nộp hồ sơ"
                type="date"
                value={form.deadline ?? ''}
                onChange={(event) => setField('deadline', event.target.value || null)}
                hint="Bỏ trống = không hạn; hết hạn sau cuối ngày đã chọn."
              />
            </div>
            <fieldset className="ui-field">
              <legend className="ui-field__label">Cơ sở tuyển</legend>
              <label className="ui-check">
                <input
                  type="checkbox"
                  checked={form.storeIds.length === 0}
                  onChange={(event) => setField('storeIds', event.target.checked ? [] : stores.slice(0, 1).map((store) => store.id))}
                />
                Toàn chuỗi (mọi cơ sở đang hoạt động)
              </label>
              {stores.map((store) => (
                <label className="ui-check" key={store.id}>
                  <input
                    type="checkbox"
                    checked={form.storeIds.includes(store.id)}
                    onChange={(event) => toggleStore(store.id, event.target.checked)}
                  />
                  {store.code} · {store.name}
                </label>
              ))}
            </fieldset>
            <MarkdownEditor label="Mô tả công việc" value={form.description} onChange={(value) => setField('description', value)} onUploadImage={uploadImage} />
            <Select label="Trạng thái" value={form.status} onChange={(event) => setField('status', event.target.value as JobStatus)}>
              <option value="OPEN">Đang mở</option>
              <option value="CLOSED">Đã đóng</option>
            </Select>
          </div>
        )}
      </Modal>
    </>
  );
};

/** ADMIN — menu Tuyển dụng: tab Tin tuyển dụng + tab Hồ sơ (spec D §4). */
export const AdminJobsPage = () => {
  const [tab, setTab] = useState<JobsTab>('postings');
  return (
    <>
      <PageHeader title="Tuyển dụng" subtitle="Tin tuyển dụng theo cơ sở và hồ sơ ứng viên của toàn chuỗi." />
      <Tabs<JobsTab>
        tabs={[
          { key: 'postings', label: 'Tin tuyển dụng' },
          { key: 'applications', label: 'Hồ sơ' },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === 'postings' ? <JobPostingsPanel /> : <JobApplicationsPanel isAdmin />}
    </>
  );
};
```

- [ ] **Step 3: `frontend/src/pages/manager/ManagerApplicationsPage.tsx`**

```tsx
import { PageHeader } from '../../components/ui';
import { JobApplicationsPanel } from '../../components/careers/JobApplicationsPanel';

/** MANAGER — hồ sơ ứng tuyển của cơ sở mình (backend khoá phạm vi; cơ sở khác → 404). */
export const ManagerApplicationsPage = () => (
  <>
    <PageHeader title="Hồ sơ ứng tuyển" subtitle="Ứng viên muốn làm việc tại cơ sở của bạn." />
    <JobApplicationsPanel isAdmin={false} />
  </>
);
```

- [ ] **Step 4: Route + menu**

`frontend/src/App.tsx`:
- import `import { AdminJobsPage } from './pages/admin/AdminJobsPage';` và `import { ManagerApplicationsPage } from './pages/manager/ManagerApplicationsPage';`
- khối `/admin`, sau route `news` (Task 14) thêm `<Route path="jobs" element={<AdminJobsPage />} />`
- khối `/staff`, trong `<Route element={<RequireRole roles={['MANAGER']} area="Quản lý cơ sở" />}>` sau `<Route path="team" element={<ManagerStaffPage />} />` thêm:

```tsx
                    <Route path="applications" element={<ManagerApplicationsPage />} />
```

`frontend/src/components/layout/navItems.ts`:
- thêm `Briefcase` vào import `lucide-react` (đầu danh sách, theo thứ tự chữ cái sau `Bike`),
- `ADMIN_NAV`: sau `{ to: '/admin/news', … }` thêm `{ to: '/admin/jobs', label: 'Tuyển dụng', icon: Briefcase },`
- `MANAGER_NAV`: sau `{ to: '/staff/team', … }` thêm `{ to: '/staff/applications', label: 'Hồ sơ ứng tuyển', icon: Briefcase },`

- [ ] **Step 5: Cổng kiểm + bấm thử**

Run (trong `frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → 26 cảnh báo (`AdminJobsPage.tsx` chỉ export `AdminJobsPage`; `JobPostingsPanel` không export).

Bấm thử:
- ADMIN `/admin/jobs` tab **Tin tuyển dụng**: thêm tin "Phụ bếp ca tối", bỏ tick Toàn chuỗi → chọn 2 cơ sở, hạn nộp = hôm qua → toast "Hạn nộp hồ sơ không được ở quá khứ"; sửa hạn hợp lệ → lưu được; **Đóng** → badge Đã đóng, khách không còn thấy tin ở `/tuyen-dung`; **Mở lại**.
- Tab **Hồ sơ**: lọc tin/cơ sở/trạng thái; bấm một dòng → ngăn chi tiết; **Tải CV** tải file đúng tên gốc; đổi "Đã liên hệ" + ghi chú → Lưu → dòng đổi badge, "Người xử lý" hiện tên admin.
- MANAGER (`manager@gmail.com`) `/staff/applications`: chỉ thấy hồ sơ cơ sở mình, không có ô chọn cơ sở.

- [ ] **Step 6: Commit (người dùng tự chạy)**

```
git add frontend/src/components/careers/JobApplicationsPanel.tsx frontend/src/pages/admin/AdminJobsPage.tsx frontend/src/pages/manager/ManagerApplicationsPage.tsx frontend/src/App.tsx frontend/src/components/layout/navItems.ts
git commit -m "feat(frontend): ADMIN quản lý tin tuyển dụng và hồ sơ; MANAGER xem/xử lý hồ sơ ứng tuyển cơ sở mình, tải CV"
```

---

## Task 16: Frontend — hộp Phản hồi (ADMIN + MANAGER) và huy hiệu số mục `NEW` trên menu

**Files:**
- Create (gốc `frontend/src/`): `pages/inbox/FeedbackInboxPage.tsx`, `hooks/useInboxCounts.ts`
- Modify: `App.tsx` (route `/admin/feedbacks`, `/staff/feedbacks`), `components/layout/navItems.ts` (type `NavBadge`, field `badge`, mục **Phản hồi**), `components/layout/DashboardLayout.tsx` (hiện huy hiệu), `styles/layout.css` (`.dash__nav-badge`)

**Interfaces:**
- Consumes: `feedbackApi.adminList/adminUpdate/countNew`, `jobApi.countNewApplications`, `Feedback`, `FeedbackType`, `FeedbackStatus`, `FEEDBACK_TYPE_LABEL`, `FEEDBACK_STATUS_LABEL`, `FEEDBACK_STATUS_TONE`, `INBOX_CHANGED_EVENT`, `notifyInboxChanged` (Task 10); `orderApi.getOrderByCode`, `OrderResponse`, `ORDER_STATUS_LABEL` (`utils/orderStatus`); `usePolling` (`hooks/usePolling`); `useAuth`; `StoreScopeSelect`.
- Produces:
  - `export type NavBadge = 'applications' | 'feedbacks'`; `NavItem.badge?: NavBadge`.
  - `useInboxCounts(enabled: boolean): Record<NavBadge, number>` (hỏi `count-new` lúc mở, mỗi 60 giây, và khi có sự kiện `bmk:inbox-changed`).
  - `FeedbackInboxPage` (route `/admin/feedbacks` cho ADMIN, `/staff/feedbacks` cho MANAGER).

- [ ] **Step 1: `navItems.ts` — kiểu huy hiệu + mục menu**

Trong `frontend/src/components/layout/navItems.ts`:
- thêm `MessageSquare` vào import `lucide-react` (theo thứ tự chữ cái, sau `LineChart`),
- sửa `interface NavItem` thành:

```ts
/** Mục menu có huy hiệu đếm số hồ sơ / phản hồi NEW trong phạm vi người dùng */
export type NavBadge = 'applications' | 'feedbacks';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  /** Khớp chính xác đường dẫn (dùng cho route gốc của phân hệ) */
  end?: boolean;
  badge?: NavBadge;
}
```

- `MANAGER_NAV` thành:

```ts
export const MANAGER_NAV: NavItem[] = [
  ...STAFF_NAV,
  { to: '/staff/reports', label: 'Báo cáo cơ sở', icon: BarChart3 },
  { to: '/staff/team', label: 'Nhân viên', icon: UsersRound },
  { to: '/staff/applications', label: 'Hồ sơ ứng tuyển', icon: Briefcase, badge: 'applications' },
  { to: '/staff/feedbacks', label: 'Phản hồi', icon: MessageSquare, badge: 'feedbacks' },
];
```

- trong `ADMIN_NAV`, sửa mục Tuyển dụng (Task 15) và thêm mục Phản hồi ngay sau:

```ts
  { to: '/admin/jobs', label: 'Tuyển dụng', icon: Briefcase, badge: 'applications' },
  { to: '/admin/feedbacks', label: 'Phản hồi', icon: MessageSquare, badge: 'feedbacks' },
```

- [ ] **Step 2: `frontend/src/hooks/useInboxCounts.ts`**

```ts
import { useCallback, useEffect, useState } from 'react';
import { feedbackApi } from '../api/feedbackApi';
import { jobApi } from '../api/jobApi';
import { INBOX_CHANGED_EVENT } from '../utils/inboxEvents';
import { usePolling } from './usePolling';
import type { NavBadge } from '../components/layout/navItems';

const POLL_MS = 60_000;

export type InboxCounts = Record<NavBadge, number>;

/**
 * Số hồ sơ ứng tuyển / phản hồi NEW trong phạm vi người dùng (MANAGER: cơ sở mình; ADMIN: toàn chuỗi).
 * Lỗi một API thì giữ số cũ của API đó, không làm hỏng menu.
 */
export const useInboxCounts = (enabled: boolean): InboxCounts => {
  const [counts, setCounts] = useState<InboxCounts>({ applications: 0, feedbacks: 0 });

  const refresh = useCallback(async () => {
    const [applications, feedbacks] = await Promise.allSettled([
      jobApi.countNewApplications(),
      feedbackApi.countNew(),
    ]);
    setCounts((prev) => ({
      applications: applications.status === 'fulfilled' ? applications.value : prev.applications,
      feedbacks: feedbacks.status === 'fulfilled' ? feedbacks.value : prev.feedbacks,
    }));
  }, []);

  useEffect(() => {
    if (!enabled) return;
    void refresh();
    const handleChanged = () => {
      void refresh();
    };
    window.addEventListener(INBOX_CHANGED_EVENT, handleChanged);
    return () => window.removeEventListener(INBOX_CHANGED_EVENT, handleChanged);
  }, [enabled, refresh]);

  usePolling(refresh, { intervalMs: POLL_MS, enabled });

  return counts;
};
```

- [ ] **Step 3: `DashboardLayout.tsx` — hiện huy hiệu**

Trong `frontend/src/components/layout/DashboardLayout.tsx`:
- thêm import `import { useInboxCounts } from '../../hooks/useInboxCounts';`
- ngay sau dòng `const BrandIcon = brand.icon;` thêm:

```tsx
  // Chỉ menu có mục gắn huy hiệu (ADMIN, MANAGER) mới hỏi count-new — STAFF/SHIPPER không gọi API này
  const inboxCounts = useInboxCounts(navItems.some((item) => item.badge !== undefined));
```

- sửa phần thân `NavLink`:

```tsx
                <span className="dash__nav-label">{item.label}</span>
              </NavLink>
```

thành:

```tsx
                <span className="dash__nav-label">{item.label}</span>
                {item.badge && inboxCounts[item.badge] > 0 && (
                  <span className="dash__nav-badge" aria-label={`${inboxCounts[item.badge]} mục mới`}>
                    {inboxCounts[item.badge] > 99 ? '99+' : inboxCounts[item.badge]}
                  </span>
                )}
              </NavLink>
```

Thêm vào `frontend/src/styles/layout.css` ngay sau khối `.dash__nav-label { … }`:

```css
.dash__nav-badge {
  margin-left: auto;
  min-width: 22px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--danger);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  line-height: 20px;
  text-align: center;
}
```

- [ ] **Step 4: `frontend/src/pages/inbox/FeedbackInboxPage.tsx`**

```tsx
import { useCallback, useEffect, useState } from 'react';
import { Eye, MessageSquare } from 'lucide-react';
import { feedbackApi } from '../../api/feedbackApi';
import { orderApi } from '../../api/orderApi';
import { useAuth } from '../../context/useAuth';
import {
  Badge,
  Button,
  ChipGroup,
  EmptyState,
  PageHeader,
  Pagination,
  Select,
  Skeleton,
  Textarea,
  useToast,
} from '../../components/ui';
import { StoreScopeSelect } from '../../components/store/StoreScopeSelect';
import { FEEDBACK_STATUS_LABEL, FEEDBACK_STATUS_TONE, FEEDBACK_TYPE_LABEL } from '../../utils/contentLabels';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { notifyInboxChanged } from '../../utils/inboxEvents';
import { ORDER_STATUS_LABEL } from '../../utils/orderStatus';
import type { Feedback, FeedbackStatus, FeedbackType } from '../../types/content';
import type { OrderResponse } from '../../types/order';
import '../../styles/components/table.css';
import '../../styles/components/content.css';

const PAGE_SIZE = 20;
const TYPES: FeedbackType[] = ['SUGGESTION', 'COMPLAINT', 'PARTNERSHIP', 'OTHER'];
const STATUSES: FeedbackStatus[] = ['NEW', 'IN_PROGRESS', 'RESOLVED'];

type StatusFilter = FeedbackStatus | 'ALL';

const STATUS_FILTERS: { value: StatusFilter; label: string }[] = [
  { value: 'ALL', label: 'Tất cả' },
  ...STATUSES.map((status) => ({ value: status, label: FEEDBACK_STATUS_LABEL[status] })),
];

interface OrderPeek {
  code: string;
  order: OrderResponse | null;
  error: string | null;
}

/** Hộp phản hồi — ADMIN mọi cơ sở (kể cả chung toàn chuỗi), MANAGER cơ sở mình (spec D §5). */
export const FeedbackInboxPage = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const toast = useToast();
  const [type, setType] = useState<FeedbackType | null>(null);
  const [storeId, setStoreId] = useState<number | null>(null);
  const [status, setStatus] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(1);
  const [items, setItems] = useState<Feedback[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(true);
  const [selected, setSelected] = useState<Feedback | null>(null);
  const [draftStatus, setDraftStatus] = useState<FeedbackStatus>('NEW');
  const [draftNote, setDraftNote] = useState('');
  const [isSaving, setIsSaving] = useState(false);
  const [orderPeek, setOrderPeek] = useState<OrderPeek | null>(null);

  const load = useCallback(async () => {
    try {
      const data = await feedbackApi.adminList({
        type: type ?? undefined,
        storeId: isAdmin ? storeId ?? undefined : undefined,
        status: status === 'ALL' ? undefined : status,
        page: page - 1,
        size: PAGE_SIZE,
      });
      setItems(data.content);
      setTotalPages(data.totalPages);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Không tải được phản hồi');
    } finally {
      setIsLoading(false);
    }
  }, [type, storeId, status, page, isAdmin, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  const select = (feedback: Feedback) => {
    setSelected(feedback);
    setDraftStatus(feedback.status);
    setDraftNote(feedback.resolutionNote ?? '');
    setOrderPeek(null);
  };

  const peekOrder = async (code: string) => {
    try {
      setOrderPeek({ code, order: await orderApi.getOrderByCode(code), error: null });
    } catch (err) {
      setOrderPeek({ code, order: null, error: err instanceof Error ? err.message : 'Không tải được đơn hàng' });
    }
  };

  const handleSave = async () => {
    if (!selected) return;
    setIsSaving(true);
    try {
      const updated = await feedbackApi.adminUpdate(selected.id, { status: draftStatus, resolutionNote: draftNote });
      toast.success('Đã cập nhật phản hồi');
      select(updated);
      notifyInboxChanged();
      await load();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Cập nhật phản hồi thất bại');
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        title="Phản hồi"
        subtitle={isAdmin ? 'Phản hồi của khách ở mọi cơ sở và phản hồi chung toàn chuỗi.' : 'Phản hồi của khách về cơ sở của bạn.'}
      />

      <div className="inbox-filters">
        <Select
          label="Loại"
          value={type ?? ''}
          onChange={(event) => {
            setType(event.target.value ? (event.target.value as FeedbackType) : null);
            setPage(1);
          }}
        >
          <option value="">Tất cả loại</option>
          {TYPES.map((value) => (
            <option key={value} value={value}>
              {FEEDBACK_TYPE_LABEL[value]}
            </option>
          ))}
        </Select>
        {isAdmin && (
          <StoreScopeSelect
            value={storeId}
            onChange={(id) => {
              setStoreId(id);
              setPage(1);
            }}
          />
        )}
        <ChipGroup<StatusFilter>
          options={STATUS_FILTERS}
          value={status}
          onChange={(value) => {
            setStatus(value);
            setPage(1);
          }}
          ariaLabel="Lọc trạng thái phản hồi"
        />
      </div>

      {isLoading ? (
        <Skeleton variant="row" count={4} />
      ) : items.length === 0 ? (
        <EmptyState icon={<MessageSquare size={30} />} title="Chưa có phản hồi" description="Phản hồi khách gửi từ trang Liên hệ sẽ hiện ở đây." />
      ) : (
        <div className={`inbox${selected ? '' : ' inbox--single'}`}>
          <section className="card">
            <div className="table-wrap">
              <table className="ui-table">
                <thead>
                  <tr>
                    <th>Tiêu đề</th>
                    <th>Loại</th>
                    <th>Cơ sở</th>
                    <th>Ngày gửi</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((feedback) => (
                    <tr
                      key={feedback.id}
                      className={`inbox__row${selected?.id === feedback.id ? ' inbox__row--active' : ''}`}
                      tabIndex={0}
                      onClick={() => select(feedback)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') select(feedback);
                      }}
                    >
                      <td>
                        <span className="ui-table__primary">{feedback.subject}</span>
                        <span className="ui-table__meta">
                          {feedback.fullName}
                          {feedback.orderCode ? ` · đơn ${feedback.orderCode}` : ''}
                        </span>
                      </td>
                      <td>{FEEDBACK_TYPE_LABEL[feedback.type]}</td>
                      <td>{feedback.storeName ?? 'Toàn chuỗi'}</td>
                      <td>{formatDateTime(feedback.createdAt)}</td>
                      <td>
                        <Badge tone={FEEDBACK_STATUS_TONE[feedback.status]}>{FEEDBACK_STATUS_LABEL[feedback.status]}</Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={page} totalPages={totalPages} onChange={setPage} />
          </section>

          {selected && (
            <aside className="card inbox__detail">
              <div className="card__body cf-form">
                <h3 className="card__title">{selected.subject}</h3>
                <dl>
                  <dt>Loại</dt>
                  <dd>{FEEDBACK_TYPE_LABEL[selected.type]}</dd>
                  <dt>Người gửi</dt>
                  <dd>
                    {selected.fullName}
                    {selected.userId ? ' (có tài khoản)' : ''}
                  </dd>
                  <dt>Điện thoại</dt>
                  <dd>{selected.phone ? <a href={`tel:${selected.phone}`}>{selected.phone}</a> : '—'}</dd>
                  <dt>Email</dt>
                  <dd>{selected.email ? <a href={`mailto:${selected.email}`}>{selected.email}</a> : '—'}</dd>
                  <dt>Cơ sở</dt>
                  <dd>{selected.storeName ?? 'Chung toàn chuỗi'}</dd>
                  <dt>Đơn hàng</dt>
                  <dd>
                    {selected.orderCode ? (
                      <>
                        {selected.orderCode}{' '}
                        <Button size="sm" variant="ghost" icon={<Eye size={14} />} onClick={() => void peekOrder(selected.orderCode as string)}>
                          Xem đơn
                        </Button>
                      </>
                    ) : (
                      '—'
                    )}
                  </dd>
                  <dt>Ngày gửi</dt>
                  <dd>{formatDateTime(selected.createdAt)}</dd>
                  <dt>Người xử lý</dt>
                  <dd>
                    {selected.handledByName
                      ? `${selected.handledByName} · ${formatDateTime(selected.handledAt)}`
                      : 'Chưa xử lý'}
                  </dd>
                </dl>

                {orderPeek && orderPeek.code === selected.orderCode && (
                  <div className="order-peek">
                    {orderPeek.order ? (
                      <>
                        <strong>
                          {orderPeek.order.orderCode} · {ORDER_STATUS_LABEL[orderPeek.order.status]}
                        </strong>
                        <span>
                          {orderPeek.order.storeName ?? '—'} · {formatDateTime(orderPeek.order.createdAt)} ·{' '}
                          {formatCurrency(orderPeek.order.total)}
                        </span>
                        <span>
                          {orderPeek.order.items.map((item) => `${item.productName} ×${item.quantity}`).join(', ')}
                        </span>
                        <span>
                          Giao tới: {orderPeek.order.receiverName} · {orderPeek.order.receiverPhone}
                        </span>
                      </>
                    ) : (
                      <span>{orderPeek.error}</span>
                    )}
                  </div>
                )}

                <div className="inbox__message">{selected.content}</div>
                <Select label="Trạng thái" value={draftStatus} onChange={(event) => setDraftStatus(event.target.value as FeedbackStatus)}>
                  {STATUSES.map((value) => (
                    <option key={value} value={value}>
                      {FEEDBACK_STATUS_LABEL[value]}
                    </option>
                  ))}
                </Select>
                <Textarea label="Ghi chú xử lý" rows={4} maxLength={2000} value={draftNote} onChange={(event) => setDraftNote(event.target.value)} />
                <div className="inbox__actions">
                  <Button loading={isSaving} onClick={() => void handleSave()}>
                    Lưu
                  </Button>
                  <Button variant="ghost" onClick={() => setSelected(null)}>
                    Đóng
                  </Button>
                </div>
              </div>
            </aside>
          )}
        </div>
      )}
    </>
  );
};
```

- [ ] **Step 5: Route trong `frontend/src/App.tsx`**

- import `import { FeedbackInboxPage } from './pages/inbox/FeedbackInboxPage';`
- khối `/admin`, sau route `jobs` (Task 15): `<Route path="feedbacks" element={<FeedbackInboxPage />} />`
- khối `/staff` nhóm `RequireRole roles={['MANAGER']}`, sau route `applications` (Task 15):

```tsx
                    <Route path="feedbacks" element={<FeedbackInboxPage />} />
```

- [ ] **Step 6: Cổng kiểm + bấm thử**

Run (trong `frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → 26 cảnh báo.

Bấm thử:
- Khách gửi 1 phản hồi gắn đơn ở cơ sở A, 1 phản hồi "Chung toàn chuỗi"; ứng viên nộp 1 hồ sơ cơ sở A.
- ADMIN: menu **Tuyển dụng** và **Phản hồi** có huy hiệu đỏ (1 và 2); `/admin/feedbacks` thấy cả 2 phản hồi; lọc cơ sở A → còn 1; mở phản hồi gắn đơn → **Xem đơn** hiện trạng thái, cơ sở, tổng tiền, món; đổi **Đang xử lý** + ghi chú → Lưu → huy hiệu Phản hồi giảm ngay còn 1.
- MANAGER cơ sở A: menu có **Hồ sơ ứng tuyển** (huy hiệu 1) và **Phản hồi**; `/staff/feedbacks` chỉ thấy phản hồi cơ sở A, không thấy phản hồi chung toàn chuỗi, không có ô chọn cơ sở.
- STAFF: menu không có hai mục này, DevTools không có request `count-new`.

- [ ] **Step 7: Commit (người dùng tự chạy)**

```
git add frontend/src/pages/inbox/FeedbackInboxPage.tsx frontend/src/hooks/useInboxCounts.ts frontend/src/App.tsx frontend/src/components/layout/navItems.ts frontend/src/components/layout/DashboardLayout.tsx frontend/src/styles/layout.css
git commit -m "feat(frontend): hộp phản hồi cho ADMIN/MANAGER (xem đơn liên quan, đổi trạng thái, ghi chú) và huy hiệu số mục mới trên menu"
```

---

## Task 17: Kiểm chứng cuối — V15 trên bản sao DB, toàn bộ test, smoke HTTP, giao diện, tài liệu

**Files:**
- Modify: `SETUP.md` (thêm mục "Tin tức, Tuyển dụng, Phản hồi — khi pull code"), `docs/superpowers/specs/2026-10-02-news-careers-feedback-design.md` (trạng thái → "Đã triển khai")

**Interfaces:**
- Consumes: toàn bộ Task 1–16; bản sao lưu trước V15 do controller tạo sẵn ở `.superpowers/sdd/2026-10-02-news-careers-feedback/backup_truoc_V15.sql`.
- Produces: bằng chứng chạy (log, kết quả lệnh) để báo người dùng; tài liệu hướng dẫn team.

- [ ] **Step 1: Chạy V15 trên bản sao DB khôi phục từ bản trước V15 (MariaDB 10.4 XAMPP, root không mật khẩu — KHÔNG truyền `-p`)**

Git Bash, thư mục gốc repo:

```
MYSQL=/c/xampp/mysql/bin/mysql.exe
$MYSQL -h 127.0.0.1 -P 3307 -u root -e "DROP DATABASE IF EXISTS banhmyking_copy; CREATE DATABASE banhmyking_copy CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
$MYSQL -h 127.0.0.1 -P 3307 -u root banhmyking_copy < .superpowers/sdd/2026-10-02-news-careers-feedback/backup_truoc_V15.sql
$MYSQL -h 127.0.0.1 -P 3307 -u root banhmyking_copy -e "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1; SELECT COUNT(*) AS orders_truoc FROM orders; SELECT COUNT(*) AS users_truoc FROM users;"
```

Expected: version `14`; ghi lại hai số đếm.

Chạy app trỏ vào bản sao (cổng 8081 để không đụng backend dev):

```
./mvnw -B spring-boot:run -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3307/banhmyking_copy?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true --server.port=8081"
```

Expected: log `Migrating schema ... to version "15 - news careers feedback"` và `Successfully applied 1 migration`, Hibernate validate không lỗi, `Started BanhmykingApplication`. Dừng app (Ctrl+C) rồi kiểm:

```
$MYSQL -h 127.0.0.1 -P 3307 -u root banhmyking_copy -e "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1; SELECT COUNT(*) AS orders_sau FROM orders; SELECT COUNT(*) AS users_sau FROM users; SELECT (SELECT COUNT(*) FROM news_posts) n, (SELECT COUNT(*) FROM job_postings) j, (SELECT COUNT(*) FROM job_posting_stores) js, (SELECT COUNT(*) FROM job_applications) a, (SELECT COUNT(*) FROM feedbacks) f; SHOW INDEX FROM job_applications; SHOW CREATE TABLE feedbacks\G"
```

Expected: version `15`; `orders_sau = orders_truoc`, `users_sau = users_truoc` (dữ liệu cũ nguyên vẹn); 5 bảng mới đều 0 dòng; có index `idx_job_applications_store_status`, `idx_job_applications_dup`; `feedbacks` có FK tới `stores`, `orders`, `users`.

Dọn: `$MYSQL -h 127.0.0.1 -P 3307 -u root -e "DROP DATABASE banhmyking_copy;"`

- [ ] **Step 2: Toàn bộ test + cổng frontend**

Run: `./mvnw -B clean test` → BUILD SUCCESS (gồm `ContentSchemaIntegrationTest`, `NewsServiceTest`, `NewsControllerTest`, `JobPostingServiceTest`, `JobControllerTest`, `CvStorageServiceTest`, `JobApplicationServiceTest`, `JobApplicationControllerTest`, `FeedbackServiceTest`, `FeedbackControllerTest`, `NewsCareersSecurityTest`, các tiện ích Task 2 và mọi test cũ).
Run (`frontend/`): `npx tsc -b` → 0 lỗi; `npm run build` → `✓ built`; `npx oxlint src` → 26 cảnh báo (không thêm).
Kiểm `git status --short` (chỉ đọc): không có file nào trong `private-uploads/` hay `target/test-private-uploads/` bị liệt kê.

- [ ] **Step 3: Smoke HTTP trên DB dev (backend `./mvnw -B spring-boot:run`, profile dev có tài khoản seed mật khẩu `12345678`)**

Viết script Python tạm trong scratchpad (không đưa vào repo), gọi `http://localhost:8080/api/v1`, đăng nhập `admin@gmail.com`, `manager@gmail.com`, `staff@gmail.com`, `customer@gmail.com` (`POST /auth/login` → `data.accessToken`); lấy `storeId` của manager qua `GET /users/me`; chọn một cơ sở khác `storeOther` từ `GET /stores` (nếu chỉ có một cơ sở, ADMIN tạo tạm cơ sở `SMK<giờ>` qua `POST /admin/stores` và xoá khi dọn). Kiểm lần lượt:

1. ADMIN `POST /admin/news` `{title:"Smoke tin đã đăng", content:"**Đậm** [link](https://example.com)", status:"PUBLISHED"}` → 201, `displayState=PUBLISHED`, `slug=smoke-tin-da-dang` (hoặc `-2`…); thêm bài `status:"PUBLISHED", publishedAt:<ngày mai>T08:00` → `SCHEDULED`; bài `DRAFT`. Khách (không token) `GET /news` có bài 1, không có bài 2–3; `GET /news/<slug-bài-2>` và `<slug-bài-3>` → 404 "Không tìm thấy bài viết"; `GET /news/latest?limit=3` → 200. MANAGER `GET /admin/news` → 403; STAFF → 403.
2. ADMIN `POST /admin/jobs` tin A `{title:"Smoke phụ bếp", employmentType:"PART_TIME", description:"## Mô tả", storeIds:[managerStore]}` và tin B toàn chuỗi; `deadline` hôm qua → 400 "Hạn nộp hồ sơ không được ở quá khứ". Khách `GET /jobs?storeId=<storeOther>` có B, không có A; `GET /jobs/<slug-A>` → `acceptingApplications=true`.
3. Khách nộp hồ sơ multipart `POST /jobs/<slug-A>/applications` (`storeId=managerStore`, `fullName="Smoke ứng viên"`, `phone=0901230001`, `cv=` file PDF thật nhỏ) → 200 "Đã gửi hồ sơ ứng tuyển…". Nộp lại cùng SĐT → 400 "Bạn đã nộp hồ sơ cho vị trí này". `storeId=storeOther` cho tin A → 400 "Cơ sở không nhận hồ sơ cho vị trí này". File `.exe` đổi tên `.pdf` → 400 "File CV phải là PDF, JPG hoặc PNG". File 6MB `.pdf` → 400 "File CV không được vượt quá 5MB". Ô `website=http://spam` → 200 nhưng `GET /job-applications?jobId=<A>` (ADMIN) không tăng số hồ sơ.
4. MANAGER `GET /job-applications` → chỉ hồ sơ cơ sở mình; `GET /job-applications/{id}/cv` của hồ sơ vừa nộp → 200, header `Content-Disposition: attachment…`, `X-Content-Type-Options: nosniff`, bytes trùng file gửi lên. Lấy `cvFileKey` bằng `SELECT cv_file_key FROM job_applications ORDER BY id DESC LIMIT 1` (mysql) → `GET /uploads/cv/<key>` và `GET /uploads/<key>` → 404. ADMIN đóng tin A (`PATCH /admin/jobs/{A}/status {status:"CLOSED"}`) → khách nộp tiếp → 400 "Tin tuyển dụng đã hết hạn nhận hồ sơ". MANAGER `GET /job-applications?storeId=<storeOther>` → 404; `PATCH /job-applications/{id} {status:"CONTACTED", internalNote:"Đã gọi"}` → 200; `GET /job-applications/count-new` giảm 1.
5. Phản hồi: khách `customer` lấy một đơn của mình `GET /orders?page=0&size=1` → `orderCode`; `POST /feedbacks` (token khách) `{type:"COMPLAINT", orderCode, subject:"Smoke phản hồi", content ≥10 ký tự, phone}` (mọi phản hồi smoke đặt tiêu đề bắt đầu bằng "Smoke") → 200; ADMIN `GET /admin/feedbacks` thấy phản hồi với `storeId` = cơ sở của đơn. Không token + `orderCode` → 404 "Không tìm thấy đơn hàng". `storeId` của cơ sở đã ngừng hoạt động → 400. Thiếu cả SĐT lẫn email → 400 "Vui lòng nhập số điện thoại hoặc email để cửa hàng liên hệ lại". Phản hồi "chung toàn chuỗi" (không storeId) → MANAGER `GET /admin/feedbacks/{id}` → 404, ADMIN → 200. Nếu `contactEmail` đã cấu hình và SMTP có thật: hộp thư nhận "[Phản hồi mới] …"; nếu không, log backend có dòng "Gửi email báo phản hồi mới … thất bại" và request vẫn 200.
6. Chống spam: tiếp tục gửi phản hồi hợp lệ từ cùng máy tới khi bị chặn → lần vượt quá 5 lần thành công trong giờ (tính cả hồ sơ đã nộp ở bước 3–5 từ `127.0.0.1`) trả **429** `errorCode=TOO_MANY_REQUESTS`, message "Bạn thao tác quá nhanh, vui lòng thử lại sau"; nộp hồ sơ ngay sau đó cũng 429 (bộ đếm chung).

Dọn dữ liệu smoke: ADMIN xoá (mềm) 3 bài và 2 tin smoke, xoá cơ sở tạm nếu đã tạo; `DELETE FROM feedbacks WHERE subject LIKE 'Smoke%'` và `DELETE FROM job_applications WHERE full_name LIKE 'Smoke%'` (mysql trên DB dev), xoá file CV smoke trong `private-uploads/cv/`. **Khởi động lại backend** để xoá bộ đếm IP trong bộ nhớ trước khi bấm thử giao diện.

- [ ] **Step 4: Danh sách bấm thử giao diện (chụp bằng Chrome như A/B, gửi người dùng)**

- `/`: khối **Tin mới** (3 bài mới nhất); header + footer có **Tin tức**, **Tuyển dụng**.
- `/tin-tuc`: lưới thẻ (ảnh bìa, ngày, tóm tắt), bài ghim đầu, phân trang; `/tin-tuc/<slug>`: bài + 3 bài liên quan, link ngoài mở tab mới, ảnh chèn hiển thị.
- `/tuyen-dung`: thẻ tin (vị trí, hình thức, lương, cơ sở, hạn nộp), lọc cơ sở; `/tuyen-dung/<slug>`: mô tả Markdown + form (chọn cơ sở, CV); tin đóng/hết hạn ẩn form.
- `/contact`: khối hotline/email giữ nguyên + form phản hồi; từ `/orders/<mã>` bấm **Phản hồi về đơn này** → form mở sẵn đơn, ô cơ sở khoá.
- `/admin/news`: chip Nháp/Hẹn giờ/Đã đăng, form có thanh công cụ + **Xem trước**, chèn ảnh, ảnh bìa, ghim, thời điểm đăng.
- `/admin/jobs`: tab **Tin tuyển dụng** (Toàn chuỗi / chọn nhiều cơ sở, đóng/mở) và tab **Hồ sơ** (ngăn chi tiết, **Tải CV**, đổi trạng thái, ghi chú).
- `/admin/feedbacks` và `/staff/feedbacks`, `/staff/applications` (MANAGER): lọc, ngăn chi tiết, **Xem đơn**, huy hiệu đỏ số mục NEW trên menu giảm sau khi xử lý.

- [ ] **Step 5: `SETUP.md` — thêm mục ngay trước `## LỖI THÌ CHỊU. HỎI CHAT.`**

```markdown
## Tin tức, Tuyển dụng, Phản hồi (từ nhánh feature/news-careers)

1. **Sao lưu DB trước khi pull**: `mysqldump -h 127.0.0.1 -P 3307 -u root -p banhmyking > backup_truoc_V15.sql` (root không mật khẩu thì bỏ `-p`).
2. `git pull`, chạy backend → log `now at version v15` (chỉ tạo 5 bảng mới, dữ liệu cũ giữ nguyên).
3. `cd frontend && npm install && npm run dev` (thêm `react-markdown`, `rehype-sanitize`).
4. ADMIN → **Tin tức**: soạn bài Markdown, xem trước, hẹn giờ bằng "Thời điểm đăng" (giờ Việt Nam), ghim bài nổi bật.
5. ADMIN → **Tuyển dụng**: tạo tin (chọn cơ sở hoặc "Toàn chuỗi", hạn nộp); tab **Hồ sơ** xem/tải CV. MANAGER xem hồ sơ cơ sở mình ở **Hồ sơ ứng tuyển**.
6. ADMIN → **Cấu hình trang web** → điền *Email liên hệ* để nhận email báo phản hồi mới; xử lý ở **Phản hồi** (MANAGER thấy phản hồi cơ sở mình).
7. **CV là dữ liệu cá nhân**: lưu ở thư mục `private-uploads/cv/` (cấu hình `app.private-upload-dir`), đã nằm trong `.gitignore`, KHÔNG phục vụ qua `/uploads/**`. Khi triển khai phải sao lưu thư mục này cùng DB.
8. Form công khai giới hạn 5 lần gửi/giờ/IP (hồ sơ + phản hồi chung, đếm trong bộ nhớ — khởi động lại backend là đếm lại). Chạy sau reverse proxy tin cậy thì đặt `app.trust-forwarded-for=true`.
```

Sửa dòng `- **Trạng thái:** Chờ duyệt` trong `docs/superpowers/specs/2026-10-02-news-careers-feedback-design.md` thành `- **Trạng thái:** Đã triển khai`.

- [ ] **Step 6: Commit (người dùng tự chạy)**

```
git add SETUP.md docs/superpowers/specs/2026-10-02-news-careers-feedback-design.md docs/superpowers/plans/2026-10-02-news-careers-feedback.md
git commit -m "docs(content): hướng dẫn team khi pull tin tức/tuyển dụng/phản hồi, cập nhật trạng thái thiết kế"
```

---

## Self-review (đã chạy khi lập kế hoạch)

**Độ phủ spec:**

| Spec | Task |
|---|---|
| §2.1–2.5 năm bảng V15 (cột, kiểu, FK, chỉ mục, xoá mềm, UNIQUE slug) | 1 |
| §3 hiển thị theo `PUBLISHED ∧ published_at ≤ now` (Clock), ghim trước, 3 bài liên quan, latest, 404 bài ẩn | 1, 3, 9 |
| §3 ràng buộc tiêu đề 3–200, nội dung, slug `[a-z0-9-]` + hậu tố, PUBLISHED không gửi thời điểm → now | 2, 3 |
| §3 giao diện khách (`/tin-tuc`, `/tin-tuc/:slug`, khối Tin mới, link header/footer) | 11 |
| §3 giao diện admin (lọc Nháp/Đã đăng/Hẹn giờ, thanh công cụ, chèn ảnh, Xem trước, slug, ảnh bìa, tóm tắt, ghim, datetime-local giờ VN) | 10, 14 |
| D1 / §3 hiển thị an toàn (react-markdown + rehype-sanitize, không HTML thô, link ngoài `noopener noreferrer`, `MarkdownView` dùng chung) | 10, 11, 12 |
| §4 API tin tuyển (lọc cơ sở/toàn chuỗi, hết hạn sau cuối ngày, `acceptingApplications`, CRUD + đóng/mở ADMIN) | 4 |
| §4 quy tắc nộp: hết hạn → 400, cơ sở thuộc tin, họ tên/SĐT VN/email, ô bẫy 200 giả, trùng SĐT 24h → 400, 5 lần/giờ/IP chung → 429, email xác nhận | 2, 6, 7, 9 |
| §4 lưu CV: thư mục riêng + `.gitignore`, UUID, ≤5MB, chữ ký khớp đuôi + loại, tải qua API kiểm quyền, attachment + nosniff, không qua `/uploads/**`, xoá mềm tin không xoá hồ sơ | 5, 6, 7, 9 |
| §4 API hồ sơ (list/get/patch/cv/count-new, MANAGER ép cơ sở, ngoài phạm vi 404) | 6, 7, 9 |
| §4 giao diện (`/tuyen-dung`, chi tiết + form, admin 2 tab, MANAGER Hồ sơ ứng tuyển, ngăn chi tiết, Tải CV, huy hiệu NEW) | 12, 15, 16 |
| §5 phản hồi: chủ đơn mới gắn được đơn (404), cơ sở theo đơn, mặc định từ tài khoản, bắt buộc + 10–5000 + SĐT/email, cơ sở đang hoạt động, ô bẫy + IP, email tới `contactEmail` (lỗi chỉ log), handled_by/at | 2, 8, 9 |
| §5 API quản lý (MANAGER cơ sở mình, `store_id NULL` chỉ ADMIN, count-new) | 8, 9 |
| §5 giao diện (form ở Liên hệ, chọn đơn → khoá cơ sở, nút ở trang theo dõi đơn, menu Phản hồi ADMIN/MANAGER, link sang đơn, huy hiệu) | 13, 16 |
| §6 phân quyền tổng hợp (khách/CUSTOMER/STAFF/MANAGER/ADMIN), ngoài phạm vi 404 | 9 |
| §7 kiểm thử (slug, Clock, hạn nộp, cơ sở hồ sơ, gắn đơn, bảo mật, CV, spam, V15 bản sao, smoke, Chrome) | 1–9, 17 |
| §8 rủi ro (bộ đếm trong bộ nhớ, CV sao lưu + không vào git + SETUP.md, slug đ/Đ) | 2, 5, 17 |

**Quét placeholder:** không còn "TBD/TODO/tương tự Task N"; mọi bước code có khối code đầy đủ; mọi lệnh có kết quả mong đợi.

**Nhất quán tên/kiểu:** `SlugUtils.{slugify, uniqueSlug, MAX_BASE_LENGTH}`; `ContactFields.{trimToNull, requireText, optionalText, phone, email}`; `SubmissionRateLimiter.{check, record, MAX_PER_WINDOW, WINDOW, MESSAGE}`; `ClientIpResolver.resolve(HttpServletRequest)`; `NewsService.{listPublished, latest, getPublished, searchAdmin, getAdmin, create(Long, NewsRequest), update, delete}`; `JobPostingService.{today, listOpen, getBySlug, requireBySlug, receivingStores, searchAdmin, getAdmin, create, update, setStatus, delete}`; `CvStorageService.{store, load}`; `JobApplicationService.{submit, search, get, update, loadCv, countNew}`; `FeedbackService.{submit(Long, FeedbackRequest, String), search, get, update, countNew}`; `EmailService.{sendApplicationConfirmation, sendFeedbackNotice}`; repository `findVisible / findVisibleExcluding / searchAdmin / findOpen / search / countByStatusInScope / existsByJobPostingIdAndPhoneAndCreatedAtAfter`; TS `newsApi`, `jobApi`, `feedbackApi`, `notifyInboxChanged`, `INBOX_CHANGED_EVENT`, `useInboxCounts`, `NavBadge`, `MarkdownView`, `MarkdownEditor`, `NewsCard`, `ApplyForm`, `JobApplicationsPanel`, `FeedbackForm`, `FeedbackInboxPage` — dùng thống nhất ở mọi task.
