package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Feedback;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /** storeId != null loại luôn phản hồi chung toàn chuỗi (store NULL) — đúng phạm vi MANAGER. */
    @EntityGraph(attributePaths = {"store", "relatedOrder", "handledBy"})
    @Query(value = """
            SELECT f FROM Feedback f
            WHERE (:type IS NULL OR f.type = :type)
              AND (:storeId IS NULL OR f.store.id = :storeId)
              AND (:status IS NULL OR f.status = :status)
            """,
            countQuery = """
            SELECT COUNT(f) FROM Feedback f
            WHERE (:type IS NULL OR f.type = :type)
              AND (:storeId IS NULL OR f.store.id = :storeId)
              AND (:status IS NULL OR f.status = :status)
            """)
    Page<Feedback> search(@Param("type") FeedbackType type, @Param("storeId") Long storeId,
                          @Param("status") FeedbackStatus status, Pageable pageable);

    @Query("SELECT COUNT(f) FROM Feedback f WHERE f.status = :status "
            + "AND (:storeId IS NULL OR f.store.id = :storeId)")
    long countByStatusInScope(@Param("status") FeedbackStatus status, @Param("storeId") Long storeId);
}
