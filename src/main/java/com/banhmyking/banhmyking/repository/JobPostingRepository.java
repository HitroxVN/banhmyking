package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.enums.JobStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, Long> {

    Optional<JobPosting> findByIdAndDeletedFalse(Long id);

    Optional<JobPosting> findBySlugAndDeletedFalse(String slug);

    /** Slug đã bị chiếm bởi bất kỳ dòng nào, kể cả tin đã xoá mềm (khớp uk_job_postings_slug). */
    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    /**
     * Tin khách thấy: OPEN, chưa xoá, chưa quá hạn. storeId != null → chỉ tin tuyển ở cơ sở đó
     * hoặc tuyển toàn chuỗi (không gắn cơ sở nào).
     */
    @Query("""
            SELECT j FROM JobPosting j
            WHERE j.deleted = false
              AND j.status = com.banhmyking.banhmyking.enums.JobStatus.OPEN
              AND (j.deadline IS NULL OR j.deadline >= :today)
              AND (:storeId IS NULL OR j.stores IS EMPTY
                   OR EXISTS (SELECT s.id FROM JobPosting j2 JOIN j2.stores s WHERE j2.id = j.id AND s.id = :storeId))
            ORDER BY j.createdAt DESC, j.id DESC
            """)
    List<JobPosting> findOpen(@Param("today") LocalDate today, @Param("storeId") Long storeId);

    @Query("""
            SELECT j FROM JobPosting j
            WHERE j.deleted = false
              AND (:status IS NULL OR j.status = :status)
              AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<JobPosting> searchAdmin(@Param("status") JobStatus status, @Param("keyword") String keyword,
                                 Pageable pageable);
}
