package com.banhmyking.banhmyking.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;

/**
 * Sinh mã đơn hàng dạng BMK-yyyyMMdd-XXXXX.
 * Hỗ trợ retry tự động khi xảy ra va chạm constraint UNIQUE.
 */
@Component
public class OrderCodeGenerator {

    private static final String PREFIX = "BMK-";
    private static final String CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int RANDOM_LENGTH = 5;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Sinh một mã đơn hàng ngẫu nhiên cơ bản dạng BMK-yyyyMMdd-XXXXX.
     */
    public String generateRawCode() {
        String datePart = LocalDate.now().format(DATE_FORMATTER);
        StringBuilder sb = new StringBuilder(PREFIX).append(datePart).append("-");
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Sinh mã đơn hàng kèm cơ chế retry kiểm tra tồn tại.
     *
     * @param existsChecker hàm kiểm tra xem mã đã tồn tại hay chưa (trả về true nếu đã tồn tại)
     * @param maxRetries    số lần retry tối đa (ví dụ: 3)
     * @return mã đơn hàng duy nhất (đã qua existsChecker)
     * @throws IllegalStateException nếu hết retry mà vẫn không tìm được mã tự do —
     *         trước đây fallback trả mã CHƯA kiểm tra → order_code UNIQUE nổ 500 khó hiểu
     *         khi save; throws sớm cho caller biết nguyên nhân thật.
     */
    public String generateUniqueCode(Predicate<String> existsChecker, int maxRetries) {
        int attempts = 0;
        while (attempts < maxRetries) {
            String code = generateRawCode();
            if (!existsChecker.test(code)) {
                return code;
            }
            attempts++;
        }
        throw new IllegalStateException(
                "Không sinh được mã đơn hàng duy nhất sau " + maxRetries + " lần thử — không gian mã quá chật hoặc trùng hàng loạt");
    }
}
