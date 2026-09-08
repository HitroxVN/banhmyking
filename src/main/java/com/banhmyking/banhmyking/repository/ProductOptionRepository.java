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
}
