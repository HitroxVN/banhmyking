package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItemId(Long orderItemId);

    Optional<Review> findByOrderItemId(Long orderItemId);

    List<Review> findByProductId(Long productId);

    @EntityGraph(attributePaths = {"user", "product", "orderItem"})
    Page<Review> findByProductId(Long productId, Pageable pageable);

    /**
     * Danh sách đánh giá cho trang quản lý (STAFF xem, ADMIN xoá).
     * Lọc tuỳ chọn: {@code null} = bỏ qua điều kiện đó.
     * EntityGraph nạp sẵn tên món + tên khách để bảng không N+1.
     */
    @EntityGraph(attributePaths = {"user", "product", "orderItem"})
    @Query("SELECT r FROM Review r "
            + "WHERE (:productId IS NULL OR r.product.id = :productId) "
            + "AND (:rating IS NULL OR r.rating = :rating)")
    Page<Review> search(@Param("productId") Long productId, @Param("rating") Integer rating, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.product.id = :productId")
    Double findAverageRatingByProductId(@Param("productId") Long productId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.product.id = :productId")
    Long countByProductId(@Param("productId") Long productId);

    /**
     * Gộp điểm trung bình + số lượt đánh giá của nhiều món trong 1 query —
     * dùng cho danh sách catalog để không phải N+1 lần gọi.
     * Mỗi phần tử: [productId, averageRating, totalReviews].
     */
    @Query("SELECT r.product.id, AVG(r.rating), COUNT(r) FROM Review r "
            + "WHERE r.product.id IN :productIds GROUP BY r.product.id")
    List<Object[]> summarizeByProductIds(@Param("productIds") Collection<Long> productIds);
}
