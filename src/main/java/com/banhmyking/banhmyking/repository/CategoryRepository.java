package com.banhmyking.banhmyking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.banhmyking.banhmyking.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
    List<Category> findByDeletedFalseOrderBySortOrderAscNameAsc();
    Optional<Category> findByIdAndDeletedFalse(Long id);
}
