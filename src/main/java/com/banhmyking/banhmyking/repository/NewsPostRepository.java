package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.NewsPost;
import com.banhmyking.banhmyking.enums.NewsStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {

    String VISIBLE = "p.deleted = false AND p.status = com.banhmyking.banhmyking.enums.NewsStatus.PUBLISHED "
            + "AND p.publishedAt <= :now";

    Optional<NewsPost> findByIdAndDeletedFalse(Long id);

    Optional<NewsPost> findBySlugAndDeletedFalse(String slug);

    /** Slug đã bị chiếm bởi bất kỳ dòng nào, kể cả bài đã xoá mềm (khớp uk_news_posts_slug). */
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /** Bài khách thấy; thứ tự truyền qua Pageable. */
    @Query("SELECT p FROM NewsPost p WHERE " + VISIBLE)
    Page<NewsPost> findVisible(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT p FROM NewsPost p WHERE " + VISIBLE + " AND p.id <> :excludeId")
    List<NewsPost> findVisibleExcluding(@Param("now") LocalDateTime now, @Param("excludeId") Long excludeId,
                                        Pageable pageable);

    /** null = bỏ qua điều kiện. publishedAfter → hẹn giờ; publishedUntil → đã đăng. */
    @Query("""
            SELECT p FROM NewsPost p
            WHERE p.deleted = false
              AND (:status IS NULL OR p.status = :status)
              AND (:publishedAfter IS NULL OR p.publishedAt > :publishedAfter)
              AND (:publishedUntil IS NULL OR p.publishedAt <= :publishedUntil)
              AND (:keyword IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<NewsPost> searchAdmin(@Param("status") NewsStatus status,
                               @Param("publishedAfter") LocalDateTime publishedAfter,
                               @Param("publishedUntil") LocalDateTime publishedUntil,
                               @Param("keyword") String keyword,
                               Pageable pageable);
}
