package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItemId(Long orderItemId);

    Optional<Review> findByOrderItemId(Long orderItemId);

    List<Review> findByProductId(Long productId);

    Page<Review> findByProductId(Long productId, Pageable pageable);
}
