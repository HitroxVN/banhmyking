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
