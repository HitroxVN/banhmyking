package com.banhmyking.banhmyking.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCodeGeneratorTest {

    private final OrderCodeGenerator generator = new OrderCodeGenerator();

    @Test
    @DisplayName("AC 1: Mã đơn hàng sinh ra phải đúng định dạng BMK-yyyyMMdd-XXXXX")
    void generateRawCode_shouldMatchFormat() {
        String code = generator.generateRawCode();

        assertThat(code).isNotNull();
        // BMK-yyyyMMdd-XXXXX (5 ký tự chữ hoa hoặc số)
        assertThat(code).matches("^BMK-\\d{8}-[A-Z0-9]{5}$");
    }

    @Test
    @DisplayName("AC 1: Tự động retry khi xảy ra va chạm mã đã tồn tại")
    void generateUniqueCode_shouldRetryWhenCollisionOccurs() {
        AtomicInteger attemptCounter = new AtomicInteger(0);

        // Giả lập 2 lần đầu bị trùng (exists = true), lần 3 thành công (exists = false)
        String uniqueCode = generator.generateUniqueCode(code -> {
            int attempt = attemptCounter.incrementAndGet();
            return attempt <= 2; // 2 lần đầu trùng
        }, 3);

        assertThat(uniqueCode).isNotNull();
        assertThat(uniqueCode).matches("^BMK-\\d{8}-[A-Z0-9]{5}$");
        assertThat(attemptCounter.get()).isEqualTo(3); // Đã retry 3 lần
    }

    @Test
    @DisplayName("Hết retry mà vẫn trùng → throw, không trả mã chưa kiểm tra")
    void generateUniqueCode_whenExhausted_shouldThrowNotReturnDirtyCode() {
        assertThatThrownBy(() -> generator.generateUniqueCode(code -> true, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Không sinh được mã đơn hàng duy nhất");
    }

    @Test
    @DisplayName("Mã đơn hàng sinh liên tiếp có tính ngẫu nhiên cao")
    void generateMultipleCodes_shouldBeUnique() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(generator.generateRawCode());
        }
        assertThat(codes).hasSize(100);
    }
}
