package com.banhmyking.banhmyking.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.banhmyking.banhmyking.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    /** Nạp bộ ảnh cho cả lưới món trong 1 query — tránh N+1 ở danh sách sản phẩm. */
    List<ProductImage> findByProductIdInOrderBySortOrderAscIdAsc(Collection<Long> productIds);

    void deleteByProduct_Id(Long productId);
}
