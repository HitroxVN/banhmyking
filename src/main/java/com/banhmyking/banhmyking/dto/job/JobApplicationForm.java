package com.banhmyking.banhmyking.dto.job;

/**
 * Trường văn bản của form ứng tuyển (multipart). Kiểm ở JobApplicationService.
 * website là ô bẫy bot: người thật không thấy ô này nên luôn để trống.
 */
public record JobApplicationForm(
        Long storeId,
        String fullName,
        String phone,
        String email,
        String message,
        String website
) {
}
