package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.enums.NewsStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.NewsPostRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.util.ContactFields;
import com.banhmyking.banhmyking.util.PageableFactory;
import com.banhmyking.banhmyking.util.SlugUtils;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tin tức (spec D §3): khách chỉ thấy bài PUBLISHED có published_at ≤ now (Clock giờ Việt Nam). */
@Service
@RequiredArgsConstructor
public class NewsService {

    public static final int RELATED_COUNT = 3;
    public static final int MAX_LATEST = 12;
    static final String NOT_FOUND = "Không tìm thấy bài viết";
    private static final String DEFAULT_SLUG = "tin-tuc";
    /** Trùng đường dẫn GET /api/v1/news/latest nên không được dùng làm slug (R9). */
    private static final String RESERVED_SLUG = "latest";

    private static final Sort PUBLIC_ORDER = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("publishedAt"),
            Sort.Order.desc("id"));
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id"));
    private static final Sort ADMIN_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NewsPostRepository newsPostRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    // ------------------------------------------------------------------ khách

    @Transactional(readOnly = true)
    public PageResponse<NewsSummaryResponse> listPublished(int page, int size) {
        return PageResponse.from(newsPostRepository.findVisible(now(), PageableFactory.of(page, size, PUBLIC_ORDER))
                .map(NewsSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public List<NewsSummaryResponse> latest(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), MAX_LATEST);
        return newsPostRepository.findVisible(now(), PageRequest.of(0, safeLimit, NEWEST_FIRST)).getContent()
                .stream().map(NewsSummaryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public NewsDetailResponse getPublished(String slug) {
        LocalDateTime now = now();
        NewsPost post = newsPostRepository.findBySlugAndDeletedFalse(slug)
                .filter(p -> p.isVisibleAt(now))
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        List<NewsPost> related = newsPostRepository.findVisibleExcluding(now, post.getId(),
                PageRequest.of(0, RELATED_COUNT, NEWEST_FIRST));
        return NewsDetailResponse.of(post, related);
    }

    // ------------------------------------------------------------------ ADMIN

    @Transactional(readOnly = true)
    public PageResponse<AdminNewsResponse> searchAdmin(NewsDisplayState state, String keyword, int page, int size) {
        LocalDateTime now = now();
        NewsStatus status = null;
        LocalDateTime publishedAfter = null;
        LocalDateTime publishedUntil = null;
        if (state == NewsDisplayState.DRAFT) {
            status = NewsStatus.DRAFT;
        } else if (state == NewsDisplayState.SCHEDULED) {
            status = NewsStatus.PUBLISHED;
            publishedAfter = now;
        } else if (state == NewsDisplayState.PUBLISHED) {
            status = NewsStatus.PUBLISHED;
            publishedUntil = now;
        }
        return PageResponse.from(newsPostRepository.searchAdmin(status, publishedAfter, publishedUntil,
                        ContactFields.trimToNull(keyword), PageableFactory.of(page, size, ADMIN_ORDER))
                .map(post -> AdminNewsResponse.from(post, now, false)));
    }

    @Transactional(readOnly = true)
    public AdminNewsResponse getAdmin(Long id) {
        return AdminNewsResponse.from(requirePost(id), now(), true);
    }

    @Transactional
    public AdminNewsResponse create(Long actorId, NewsRequest request) {
        NewsPost post = new NewsPost();
        post.setAuthor(userRepository.findById(actorId).orElse(null));
        apply(post, request);
        return AdminNewsResponse.from(newsPostRepository.save(post), now(), true);
    }

    @Transactional
    public AdminNewsResponse update(Long id, NewsRequest request) {
        NewsPost post = requirePost(id);
        apply(post, request);
        return AdminNewsResponse.from(newsPostRepository.save(post), now(), true);
    }

    @Transactional
    public void delete(Long id) {
        NewsPost post = requirePost(id);
        post.setDeleted(true);
        newsPostRepository.save(post);
    }

    // ------------------------------------------------------------------ nội bộ

    private void apply(NewsPost post, NewsRequest request) {
        String title = ContactFields.trimToNull(request.title());
        if (title == null || title.length() < 3 || title.length() > 200) {
            throw invalid("Tiêu đề phải từ 3 đến 200 ký tự");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw invalid("Nội dung bài viết không được để trống");
        }
        String summary = ContactFields.optionalText(request.summary(), 500, "Tóm tắt tối đa 500 ký tự");
        String cover = ContactFields.optionalText(request.coverImageUrl(), 500, "Đường dẫn ảnh bìa tối đa 500 ký tự");
        String slug = resolveSlug(post, request.slug(), title);
        NewsStatus status = request.status() == null ? NewsStatus.DRAFT : request.status();

        post.setTitle(title);
        post.setContent(request.content());
        post.setSummary(summary);
        post.setCoverImageUrl(cover);
        post.setSlug(slug);
        NewsStatus previousStatus = post.getStatus();
        LocalDateTime previousPublishedAt = post.getPublishedAt();
        post.setStatus(status);
        LocalDateTime publishedAt;
        if (request.publishedAt() != null) {
            publishedAt = request.publishedAt().truncatedTo(ChronoUnit.SECONDS);
        } else if (status != NewsStatus.PUBLISHED) {
            publishedAt = null;
        } else if (previousStatus == NewsStatus.PUBLISHED && previousPublishedAt != null) {
            publishedAt = previousPublishedAt; // đã đăng: giữ nguyên ngày đăng, không đẩy lên đầu danh sách
        } else {
            publishedAt = now();
        }
        post.setPublishedAt(publishedAt);
        post.setPinned(Boolean.TRUE.equals(request.pinned()));
    }

    private String resolveSlug(NewsPost post, String requested, String title) {
        Long selfId = post.getId();
        String wanted;
        if (ContactFields.trimToNull(requested) != null) {
            wanted = SlugUtils.slugify(requested);
            if (wanted.isEmpty()) {
                throw invalid("Slug chỉ gồm chữ thường không dấu, số và dấu gạch ngang");
            }
        } else if (selfId != null) {
            return post.getSlug();
        } else {
            wanted = SlugUtils.slugify(title);
            if (wanted.isEmpty()) {
                wanted = DEFAULT_SLUG;
            }
        }
        if (wanted.equals(post.getSlug())) {
            return wanted;
        }
        return SlugUtils.uniqueSlug(wanted, candidate -> RESERVED_SLUG.equals(candidate)
                || (selfId == null
                        ? newsPostRepository.existsBySlug(candidate)
                        : newsPostRepository.existsBySlugAndIdNot(candidate, selfId)));
    }

    private NewsPost requirePost(Long id) {
        return newsPostRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    /** Cắt về giây nguyên (R12) để so sánh/lưu khớp với cột DATETIME không có phần thập phân. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
