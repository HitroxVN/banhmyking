package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    /** Chống nộp trùng: cùng SĐT + cùng tin sau mốc `since` (spec D §4). */
    boolean existsByJobPostingIdAndPhoneAndCreatedAtAfter(Long jobPostingId, String phone, LocalDateTime since);

    // Nạp sẵn các quan hệ to-one hiển thị trên danh sách (tránh N+1); countQuery riêng, không join.
    @EntityGraph(attributePaths = {"jobPosting", "store", "handledBy"})
    @Query(value = """
            SELECT a FROM JobApplication a
            WHERE (:jobId IS NULL OR a.jobPosting.id = :jobId)
              AND (:storeId IS NULL OR a.store.id = :storeId)
              AND (:status IS NULL OR a.status = :status)
            """,
            countQuery = """
            SELECT COUNT(a) FROM JobApplication a
            WHERE (:jobId IS NULL OR a.jobPosting.id = :jobId)
              AND (:storeId IS NULL OR a.store.id = :storeId)
              AND (:status IS NULL OR a.status = :status)
            """)
    Page<JobApplication> search(@Param("jobId") Long jobId, @Param("storeId") Long storeId,
                                @Param("status") ApplicationStatus status, Pageable pageable);

    /** storeId null = toàn chuỗi (ADMIN). */
    @Query("SELECT COUNT(a) FROM JobApplication a WHERE a.status = :status "
            + "AND (:storeId IS NULL OR a.store.id = :storeId)")
    long countByStatusInScope(@Param("status") ApplicationStatus status, @Param("storeId") Long storeId);
}
