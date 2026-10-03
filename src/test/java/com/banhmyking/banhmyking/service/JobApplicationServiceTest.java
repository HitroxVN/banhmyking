package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    void confirmationMailIsSentOnlyAfterCommitAndSkippedOnRollback() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(service.submit("phu-bep", new JobApplicationForm(1L, "Nguyễn Văn An", "0901234567",
                    "an@banhmy.vn", null, null), null, IP)).isTrue();
            verify(emailService, never()).sendApplicationConfirmation(any(), any(), any(), any());
            List<TransactionSynchronization> syncs =
                    List.copyOf(TransactionSynchronizationManager.getSynchronizations());
            syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(emailService, never()).sendApplicationConfirmation(any(), any(), any(), any());
            syncs.forEach(TransactionSynchronization::afterCommit);
            verify(emailService).sendApplicationConfirmation("an@banhmy.vn", "Nguyễn Văn An", "Phụ bếp ca tối",
                    "Cơ sở CS-A");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void mailFailureAfterCommitDoesNotPropagate() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("SMTP chết")).when(emailService)
                .sendApplicationConfirmation(any(), any(), any(), any());
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit("phu-bep", new JobApplicationForm(1L, "Nguyễn Văn An", "0901234567",
                    "an@banhmy.vn", null, null), null, IP);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(emailService).sendApplicationConfirmation(any(), any(), any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void mailFailureNeverFailsTheSubmission() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException("SMTP chết")).when(emailService)
                .sendApplicationConfirmation(any(), any(), any(), any());

        boolean saved = service.submit("phu-bep", new JobApplicationForm(1L, "Nguyễn Văn An", "0901234567",
                "an@banhmy.vn", null, null), null, IP);

        assertThat(saved).isTrue();
        verify(jobApplicationRepository).save(any(JobApplication.class));
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

    @Test
    void postingWithAllLinkedStoresInactiveIsRejectedAsNotAccepting() {
        storeA.setActive(false);
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.submit("phu-bep", form(1L, "0901234567"), null, IP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tin tuyển dụng đã hết hạn nhận hồ sơ");
        verify(jobApplicationRepository, never()).save(any());
    }

    @Test
    void cvIsDeletedWhenSavingTheApplicationFails() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        String key = "0123456789abcdef0123456789abcdef.pdf";
        when(cvStorageService.store(cv)).thenReturn(new StoredCv(key, "cv.pdf", "application/pdf"));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenThrow(new IllegalStateException("DB chết"));

        assertThatThrownBy(() -> service.submit("phu-bep", form(1L, "0901234567"), cv, IP))
                .isInstanceOf(IllegalStateException.class);

        verify(cvStorageService).delete(key);
        verify(emailService, never()).sendApplicationConfirmation(any(), any(), any(), any());
    }

    @Test
    void cvIsDeletedWhenTheTransactionRollsBackAfterSaveButKeptOnCommit() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        MockMultipartFile cv = new MockMultipartFile("cv", "cv.pdf", "application/pdf",
                "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        String key = "0123456789abcdef0123456789abcdef.pdf";
        when(cvStorageService.store(cv)).thenReturn(new StoredCv(key, "cv.pdf", "application/pdf"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.submit("phu-bep", form(1L, "0901234567"), cv, IP);
            List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
            assertThat(syncs).hasSize(1);

            syncs.get(0).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            verify(cvStorageService, never()).delete(any());

            syncs.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(cvStorageService).delete(key);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void honeypotSubmissionsDoNotConsumeTheIpLimit() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        for (int i = 0; i < 6; i++) {
            assertThat(service.submit("phu-bep",
                    new JobApplicationForm(1L, "Bot", "0901234567", null, null, "http://spam.example"), null, IP))
                    .isFalse();
        }

        assertThat(service.submit("phu-bep", form(1L, "0911111111"), null, IP)).isTrue();
    }

    @Test
    void failedSubmissionsDoNotConsumeTheIpLimit() {
        when(jobPostingRepository.findBySlugAndDeletedFalse("phu-bep")).thenReturn(Optional.of(job));
        when(jobApplicationRepository.existsByJobPostingIdAndPhoneAndCreatedAtAfter(10L, "0901234567",
                NOW.minusHours(24))).thenReturn(true);
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> service.submit("phu-bep", form(1L, "0901234567"), null, IP))
                    .hasMessage("Bạn đã nộp hồ sơ cho vị trí này");
            assertThatThrownBy(() -> service.submit("phu-bep", form(1L, " "), null, IP))
                    .isInstanceOf(BusinessException.class);
        }

        assertThat(service.submit("phu-bep", form(1L, "0911111111"), null, IP)).isTrue();
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
