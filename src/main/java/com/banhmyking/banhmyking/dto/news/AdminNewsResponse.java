package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import java.time.LocalDateTime;

/** Bài cho màn quản trị — kèm trạng thái hiển thị suy ra (Nháp / Hẹn giờ / Đã đăng). */
public record AdminNewsResponse(
        Long id,
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        String content,
        NewsStatus status,
        NewsDisplayState displayState,
        LocalDateTime publishedAt,
        boolean pinned,
        String authorName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** withContent = false cho danh sách (không kéo MEDIUMTEXT về bảng). */
    public static AdminNewsResponse from(NewsPost post, LocalDateTime now, boolean withContent) {
        return new AdminNewsResponse(post.getId(), post.getTitle(), post.getSlug(), post.getCoverImageUrl(),
                post.getSummary(), withContent ? post.getContent() : null, post.getStatus(),
                post.displayStateAt(now), post.getPublishedAt(), post.isPinned(),
                post.getAuthor() == null ? null : post.getAuthor().getFullName(),
                post.getCreatedAt(), post.getUpdatedAt());
    }
}
