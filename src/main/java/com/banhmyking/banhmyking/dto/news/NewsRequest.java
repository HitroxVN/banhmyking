package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.enums.NewsStatus;
import java.time.LocalDateTime;

/**
 * Tạo/sửa bài. Kiểm ở NewsService (thông báo tiếng Việt). slug bỏ trống: tạo → sinh từ tiêu đề,
 * sửa → giữ slug cũ. publishedAt là giờ Việt Nam; PUBLISHED mà bỏ trống → server đặt "bây giờ".
 */
public record NewsRequest(
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        String content,
        NewsStatus status,
        LocalDateTime publishedAt,
        Boolean pinned
) {
}
