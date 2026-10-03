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
