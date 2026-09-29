package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {
    List<ProductOption> findByIdInAndProductId(Collection<Long> ids, Long productId);
    List<ProductOption> findByProductId(Long productId);

    /** Thứ tự trong nhóm chỉ còn dựa vào id — bảng không có cột sort_order. */
    List<ProductOption> findByProductIdOrderByIdAsc(Long productId);

    /** Nạp option cho cả một trang món trong 1 query thay vì gọi theo từng món (N+1). */
    List<ProductOption> findByProductIdInOrderByIdAsc(Collection<Long> productIds);
}
