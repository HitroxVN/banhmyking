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
