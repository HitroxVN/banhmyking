package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {
    List<OptionGroup> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    /** Nạp nhóm cho cả một trang món trong 1 query thay vì gọi theo từng món (N+1). */
    List<OptionGroup> findByProductIdInOrderBySortOrderAscIdAsc(Collection<Long> productIds);

    void deleteByProduct_Id(Long productId);
}
