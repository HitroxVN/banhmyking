package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.config.TimeConfig;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.NewsPostRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    /** 10:00 sáng 02/10/2026 giờ Việt Nam. */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 10, 0);

    @Mock private NewsPostRepository newsPostRepository;
    @Mock private UserRepository userRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-02T03:00:00Z"), TimeConfig.VIETNAM);
    private NewsService service;

    @BeforeEach
    void setUp() {
        service = new NewsService(newsPostRepository, userRepository, clock);
    }

    @Test
    void createGeneratesSlugWithoutDiacriticsAndAddsSuffixWhenTaken() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "Quản trị")));
        when(newsPostRepository.existsBySlug("khuyen-mai-thang-10-dac-biet")).thenReturn(true);
        when(newsPostRepository.existsBySlug("khuyen-mai-thang-10-dac-biet-2")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(11L));

        AdminNewsResponse created = service.create(7L,
                request("Khuyến mãi tháng 10 — Đặc biệt", null, NewsStatus.DRAFT, null));

        assertThat(created.id()).isEqualTo(11L);
        assertThat(created.slug()).isEqualTo("khuyen-mai-thang-10-dac-biet-2");
        assertThat(created.displayState()).isEqualTo(NewsDisplayState.DRAFT);
        assertThat(created.authorName()).isEqualTo("Quản trị");
        assertThat(created.content()).isEqualTo("# Nội dung");
    }

    @Test
    void latestIsReservedSlugAndGetsSuffix() {
        when(newsPostRepository.existsBySlug("latest-2")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(14L));

        AdminNewsResponse fromTitle = service.create(7L, request("Latest", null, NewsStatus.DRAFT, null));
        AdminNewsResponse fromSlug = service.create(7L, request("Tiêu đề khác", "LATEST", NewsStatus.DRAFT, null));

        assertThat(fromTitle.slug()).isEqualTo("latest-2");
        assertThat(fromSlug.slug()).isEqualTo("latest-2");
        verify(newsPostRepository, never()).existsBySlug("latest");
    }

    @Test
    void publishWithoutPublishedAtUsesClockNow() {
        when(newsPostRepository.existsBySlug("ra-mat-banh-mi-moi")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(12L));

        AdminNewsResponse created = service.create(7L, request("Ra mắt bánh mì mới", null, NewsStatus.PUBLISHED, null));

        assertThat(created.publishedAt()).isEqualTo(NOW);
        assertThat(created.displayState()).isEqualTo(NewsDisplayState.PUBLISHED);
    }

    @Test
    void publishedAtIsTruncatedToWholeSeconds() {
        when(newsPostRepository.existsBySlug("bai-giay-le")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(15L));

        AdminNewsResponse created = service.create(7L, request("Bài giây lẻ", null, NewsStatus.PUBLISHED,
                NOW.minusDays(1).withSecond(7).withNano(987_654_321)));

        assertThat(created.publishedAt()).isEqualTo(NOW.minusDays(1).withSecond(7));
    }

    @Test
    void futurePublishedAtIsScheduledAndAdminSlugIsNormalised() {
        when(newsPostRepository.existsBySlug("hen-gio-dang")).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(saveWithId(13L));

        AdminNewsResponse created = service.create(7L,
                request("Bài hẹn giờ", "  Hẹn Giờ Đăng ", NewsStatus.PUBLISHED, NOW.plusDays(1)));

        assertThat(created.slug()).isEqualTo("hen-gio-dang");
        assertThat(created.displayState()).isEqualTo(NewsDisplayState.SCHEDULED);
        assertThat(created.publishedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void rejectsShortTitleBlankContentAndUnusableSlug() {
        assertThatThrownBy(() -> service.create(7L, request("Ab", null, NewsStatus.DRAFT, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Tiêu đề phải từ 3 đến 200 ký tự");
        assertThatThrownBy(() -> service.create(7L,
                new NewsRequest("Tiêu đề hợp lệ", null, null, null, "   ", NewsStatus.DRAFT, null, false)))
                .hasMessage("Nội dung bài viết không được để trống");
        assertThatThrownBy(() -> service.create(7L, request("Tiêu đề hợp lệ", "!!!", NewsStatus.DRAFT, null)))
                .hasMessage("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang");
        verify(newsPostRepository, never()).save(any());
    }

    @Test
    void updatingAlreadyPublishedPostWithoutDateKeepsExistingPublishedAt() {
        NewsPost existing = post(6L, "da-dang", NewsStatus.PUBLISHED, NOW.minusDays(3));
        when(newsPostRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(existing));
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminNewsResponse updated = service.update(6L, request("Tiêu đề đã sửa", "", NewsStatus.PUBLISHED, null));

        assertThat(updated.publishedAt()).isEqualTo(NOW.minusDays(3));
    }

    @Test
    void publishingDraftWithoutDateStampsNow() {
        NewsPost existing = post(7L, "ban-nhap", NewsStatus.DRAFT, null);
        when(newsPostRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(existing));
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminNewsResponse updated = service.update(7L, request("Tiêu đề đã sửa", "", NewsStatus.PUBLISHED, null));

        assertThat(updated.publishedAt()).isEqualTo(NOW);
    }

    @Test
    void updateWithBlankSlugKeepsExistingSlug() {
        NewsPost existing = post(5L, "slug-cu", NewsStatus.PUBLISHED, NOW.minusDays(1));
        when(newsPostRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(existing));
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminNewsResponse updated = service.update(5L,
                request("Tiêu đề mới hoàn toàn", "", NewsStatus.PUBLISHED, NOW.minusDays(1)));

        assertThat(updated.slug()).isEqualTo("slug-cu");
        assertThat(updated.title()).isEqualTo("Tiêu đề mới hoàn toàn");
        verify(newsPostRepository, never()).existsBySlugAndIdNot(anyString(), anyLong());
    }

    @Test
    void updateWithNewSlugOnlyChecksOtherRows() {
        when(newsPostRepository.findByIdAndDeletedFalse(5L))
                .thenReturn(Optional.of(post(5L, "slug-cu", NewsStatus.DRAFT, null)));
        when(newsPostRepository.existsBySlugAndIdNot("slug-moi", 5L)).thenReturn(false);
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(5L, request("Tiêu đề", "slug-moi", NewsStatus.DRAFT, null)).slug())
                .isEqualTo("slug-moi");
    }

    @Test
    void publicDetailHidesDraftScheduledAndMissingPosts() {
        when(newsPostRepository.findBySlugAndDeletedFalse("nhap"))
                .thenReturn(Optional.of(post(1L, "nhap", NewsStatus.DRAFT, null)));
        when(newsPostRepository.findBySlugAndDeletedFalse("hen-gio"))
                .thenReturn(Optional.of(post(2L, "hen-gio", NewsStatus.PUBLISHED, NOW.plusMinutes(1))));
        when(newsPostRepository.findBySlugAndDeletedFalse("khong-co")).thenReturn(Optional.empty());

        for (String slug : List.of("nhap", "hen-gio", "khong-co")) {
            assertThatThrownBy(() -> service.getPublished(slug))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Không tìm thấy bài viết");
        }
    }

    @Test
    void publicDetailReturnsPostWithUpToThreeRelated() {
        when(newsPostRepository.findBySlugAndDeletedFalse("bai-chinh"))
                .thenReturn(Optional.of(post(3L, "bai-chinh", NewsStatus.PUBLISHED, NOW)));
        when(newsPostRepository.findVisibleExcluding(eq(NOW), eq(3L), any(Pageable.class)))
                .thenReturn(List.of(post(4L, "b4", NewsStatus.PUBLISHED, NOW.minusDays(1)),
                        post(5L, "b5", NewsStatus.PUBLISHED, NOW.minusDays(2))));

        NewsDetailResponse detail = service.getPublished("bai-chinh");

        assertThat(detail.content()).isEqualTo("# Nội dung");
        assertThat(detail.related()).extracting(NewsSummaryResponse::id).containsExactly(4L, 5L);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsPostRepository).findVisibleExcluding(eq(NOW), eq(3L), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void adminScheduledFilterMeansPublishedAfterNow() {
        when(newsPostRepository.searchAdmin(eq(NewsStatus.PUBLISHED), eq(NOW), isNull(), eq("khuyen"),
                any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(9L, "km", NewsStatus.PUBLISHED, NOW.plusDays(2)))));

        PageResponse<AdminNewsResponse> page = service.searchAdmin(NewsDisplayState.SCHEDULED, " khuyen ", 0, 20);

        assertThat(page.content()).singleElement().satisfies(item -> {
            assertThat(item.displayState()).isEqualTo(NewsDisplayState.SCHEDULED);
            assertThat(item.content()).isNull();
        });
    }

    @Test
    void latestClampsLimitToTwelve() {
        when(newsPostRepository.findVisible(eq(NOW), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.latest(100);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(newsPostRepository).findVisible(eq(NOW), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(12);
    }

    @Test
    void deleteIsSoft() {
        NewsPost existing = post(6L, "xoa", NewsStatus.DRAFT, null);
        when(newsPostRepository.findByIdAndDeletedFalse(6L)).thenReturn(Optional.of(existing));

        service.delete(6L);

        assertThat(existing.isDeleted()).isTrue();
        verify(newsPostRepository).save(existing);
    }

    // ----------------------------------------------------------------------- helpers

    private static NewsRequest request(String title, String slug, NewsStatus status, LocalDateTime publishedAt) {
        return new NewsRequest(title, slug, null, "Tóm tắt", "# Nội dung", status, publishedAt, false);
    }

    private static NewsPost post(Long id, String slug, NewsStatus status, LocalDateTime publishedAt) {
        NewsPost post = new NewsPost();
        post.setId(id);
        post.setTitle("Bài " + slug);
        post.setSlug(slug);
        post.setContent("# Nội dung");
        post.setStatus(status);
        post.setPublishedAt(publishedAt);
        return post;
    }

    private static User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        return user;
    }

    private static Answer<NewsPost> saveWithId(Long id) {
        return inv -> {
            NewsPost post = inv.getArgument(0);
            post.setId(id);
            return post;
        };
    }
}
