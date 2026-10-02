package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Bài tin tức / khuyến mãi, nội dung Markdown (spec D §2.1). */
@Getter
@Setter
@Entity
@Table(name = "news_posts")
public class NewsPost extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(length = 500)
    private String summary;

    /** columnDefinition để ddl validate khớp MEDIUMTEXT (như SiteSetting dùng TEXT). */
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsStatus status = NewsStatus.DRAFT;

    /** Giờ Việt Nam. Bắt buộc khi PUBLISHED; ở tương lai = hẹn giờ. */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private boolean pinned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    /** Khách thấy bài khi PUBLISHED ∧ published_at ≤ now ∧ chưa xoá (spec D §2.1). */
    public boolean isVisibleAt(LocalDateTime now) {
        return !deleted && status == NewsStatus.PUBLISHED && publishedAt != null && !publishedAt.isAfter(now);
    }

    public NewsDisplayState displayStateAt(LocalDateTime now) {
        if (status == NewsStatus.DRAFT) {
            return NewsDisplayState.DRAFT;
        }
        return publishedAt != null && publishedAt.isAfter(now) ? NewsDisplayState.SCHEDULED : NewsDisplayState.PUBLISHED;
    }
}
