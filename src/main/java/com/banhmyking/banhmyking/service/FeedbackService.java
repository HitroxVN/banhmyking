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
import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.InboxType;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    /** Báo huy hiệu hộp thư của admin/manager cập nhật ngay (realtime) */
    private final ApplicationEventPublisher eventPublisher;

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
        eventPublisher.publishEvent(new InboxChangedEvent(InboxType.FEEDBACK, store == null ? null : store.getId()));
        rateLimiter.record(clientIp);

        FeedbackNotice notice = FeedbackNotice.from(feedback);
        runAfterCommit(() -> notifyContactEmail(notice));
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
            // Số "mới" trên huy hiệu của người khác vừa đổi
            eventPublisher.publishEvent(new InboxChangedEvent(InboxType.FEEDBACK,
                    feedback.getStore() == null ? null : feedback.getStore().getId()));
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

    /**
     * Chạy sau khi giao dịch commit (không có giao dịch thì chạy ngay) để lỗi đọc cấu hình/gửi mail
     * không bao giờ làm rollback hay hỏng yêu cầu; mọi RuntimeException chỉ được ghi log.
     */
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

    /** Chạy sau commit: đọc cấu hình + gửi mail; lỗi chỉ ghi log. */
    private void notifyContactEmail(FeedbackNotice notice) {
        try {
            String to = ContactFields.trimToNull(siteSettingService.getPublicSettings().get(SiteSettingKeys.CONTACT_EMAIL));
            if (to != null) {
                emailService.sendFeedbackNotice(to, notice);
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
