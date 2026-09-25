package com.banhmyking.banhmyking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.banhmyking.banhmyking.entity.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedFalse(Long id);
    List<Product> findByDeletedFalseOrderByFeaturedDescNameAsc();
    List<Product> findByCategoryIdAndDeletedFalseOrderByFeaturedDescNameAsc(Long categoryId);
    List<Product> findByAvailableTrueAndDeletedFalseOrderByFeaturedDescNameAsc();
    List<Product> findByCategoryIdAndAvailableTrueAndDeletedFalseOrderByFeaturedDescNameAsc(Long categoryId);
}
