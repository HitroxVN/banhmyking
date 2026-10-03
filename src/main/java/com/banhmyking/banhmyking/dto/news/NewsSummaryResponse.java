package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.entity.NewsPost;
import java.time.LocalDateTime;

/** Thẻ bài trên lưới tin tức / khối "Tin mới" — không chứa nội dung. */
public record NewsSummaryResponse(
        Long id,
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        LocalDateTime publishedAt,
        boolean pinned
) {
    public static NewsSummaryResponse from(NewsPost post) {
        return new NewsSummaryResponse(post.getId(), post.getTitle(), post.getSlug(), post.getCoverImageUrl(),
                post.getSummary(), post.getPublishedAt(), post.isPinned());
    }
}
