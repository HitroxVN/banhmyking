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
