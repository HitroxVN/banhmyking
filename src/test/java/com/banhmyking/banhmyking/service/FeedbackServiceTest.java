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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    void emailIsSentOnlyAfterCommitAndNotWhenTransactionRollsBack() {
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(storeA));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.submit(null, request(1L, null), IP)).isTrue();
            verifyNoInteractions(emailService, siteSettingService);
            // rollback: afterCommit never invoked
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(emailService, siteSettingService);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void emailIsSentAfterCommitAndSettingsOrMailFailureDoesNotPropagate() {
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(storeA));
        when(siteSettingService.getPublicSettings()).thenThrow(new IllegalStateException("settings down"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.submit(null, request(1L, null), IP)).isTrue();
            verifyNoInteractions(emailService, siteSettingService);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(siteSettingService).getPublicSettings();
            verifyNoInteractions(emailService);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void emailIsSentAfterCommit() {
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(storeA));
        when(siteSettingService.getPublicSettings()).thenReturn(Map.of("contactEmail", "admin@banhmy.vn"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.submit(null, request(1L, null), IP)).isTrue();
            verify(emailService, never()).sendFeedbackNotice(anyString(), any(FeedbackNotice.class));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(emailService).sendFeedbackNotice(eq("admin@banhmy.vn"), any(FeedbackNotice.class));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
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
