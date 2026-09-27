package com.banhmyking.banhmyking.scheduler;

import com.banhmyking.banhmyking.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job định kỳ dọn đơn online treo: đơn chưa thanh toán quá hạn bị tự huỷ để trả lại
 * lượt khuyến mãi và không giữ chỗ món. Đơn COD không bị đụng tới (trả tiền lúc nhận hàng).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "order.auto-cancel.enabled", havingValue = "true", matchIfMissing = true)
public class OrderMaintenanceJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${order.auto-cancel-interval-ms:600000}",
            initialDelayString = "${order.auto-cancel-initial-delay-ms:60000}")
    public void cancelStalePendingOrders() {
        try {
            int cancelled = orderService.cancelStalePendingOrders();
            if (cancelled > 0) {
                log.info("Đã tự động huỷ {} đơn treo chưa thanh toán", cancelled);
            }
        } catch (Exception e) {
            // Nuốt lỗi để scheduler không chết — lần chạy sau sẽ thử lại.
            log.error("Lỗi khi tự huỷ đơn treo chưa thanh toán", e);
        }
    }
}
