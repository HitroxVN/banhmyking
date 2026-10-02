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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        List<Store> receiving = jobPostingService.receivingStores(job);
        if (!jobPostingService.isAccepting(job, receiving, LocalDate.now(clock))) {
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
        Store store = receiving.stream()
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
        if (storedCv != null) {
            deleteCvIfNotCommitted(storedCv.fileKey());
        }

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
        try {
            jobApplicationRepository.save(application);
        } catch (RuntimeException ex) {
            if (storedCv != null) {
                cvStorageService.delete(storedCv.fileKey());
            }
            throw ex;
        }
        rateLimiter.record(clientIp);

        if (email != null) {
            String jobTitle = job.getTitle();
            String storeName = store.getName();
            // Gửi sau commit; lỗi gửi mail (kể cả lỗi ngoài SMTP) không được làm hỏng lần nộp.
            runAfterCommit(() -> {
                try {
                    emailService.sendApplicationConfirmation(email, fullName, jobTitle, storeName);
                } catch (RuntimeException ex) {
                    // Không ghi email ứng viên vào log (dữ liệu cá nhân) — chỉ mã hồ sơ
                    log.warn("Không gửi được email xác nhận cho hồ sơ ứng tuyển #{}: {}", application.getId(), ex.getMessage());
                }
            });
        }
        return true;
    }

    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    /** Giao dịch không commit (rollback do lỗi lúc flush/commit...) thì xoá CV vừa lưu, tránh file mồ côi. */
    private void deleteCvIfNotCommitted(String fileKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                // Chỉ xoá khi chắc chắn đã rollback — STATUS_UNKNOWN có thể đã commit, giữ file cho an toàn
                if (status == STATUS_ROLLED_BACK) {
                    cvStorageService.delete(fileKey);
                }
            }
        });
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
