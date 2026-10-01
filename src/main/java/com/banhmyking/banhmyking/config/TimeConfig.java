package com.banhmyking.banhmyking.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Giờ Việt Nam dùng chung — inject Clock để test giờ mở cửa không phụ thuộc giờ máy. */
@Configuration
public class TimeConfig {

    public static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    public Clock clock() {
        return Clock.system(VIETNAM);
    }
}
