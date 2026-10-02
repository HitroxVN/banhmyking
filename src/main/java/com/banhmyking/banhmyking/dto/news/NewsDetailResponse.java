package com.banhmyking.banhmyking.dto.news;

import com.banhmyking.banhmyking.entity.NewsPost;
import java.time.LocalDateTime;
import java.util.List;

/** Trang bài viết của khách: nội dung Markdown + tối đa 3 bài liên quan mới nhất. */
public record NewsDetailResponse(
        Long id,
        String title,
        String slug,
        String coverImageUrl,
        String summary,
        String content,
        LocalDateTime publishedAt,
        List<NewsSummaryResponse> related
) {
    public static NewsDetailResponse of(NewsPost post, List<NewsPost> related) {
        return new NewsDetailResponse(post.getId(), post.getTitle(), post.getSlug(), post.getCoverImageUrl(),
                post.getSummary(), post.getContent(), post.getPublishedAt(),
                related.stream().map(NewsSummaryResponse::from).toList());
    }
}
