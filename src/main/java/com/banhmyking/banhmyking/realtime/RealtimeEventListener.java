package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Chỉ đẩy tin SAU KHI giao dịch đã lưu — trình duyệt gọi lại API sẽ thấy dữ liệu mới;
 * giao dịch rollback thì không gửi gì. fallbackExecution: sự kiện phát ngoài giao dịch vẫn được gửi.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeEventListener {

    private final RealtimeHub hub;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderChanged(OrderChangedEvent event) {
        // Ngoài giao dịch (fallbackExecution) lỗi sẽ lọt vào nghiệp vụ — nuốt tại đây
        try {
            hub.publish(event);
        } catch (RuntimeException e) {
            log.warn("Không gửi được tin realtime cho đơn {}", event.orderCode(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onInboxChanged(InboxChangedEvent event) {
        try {
            hub.publish(event);
        } catch (RuntimeException e) {
            log.warn("Không gửi được tin realtime hộp thư", e);
        }
    }
}
