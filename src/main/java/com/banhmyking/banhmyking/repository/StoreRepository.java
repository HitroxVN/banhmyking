package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.Store;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoreRepository extends JpaRepository<Store, Long> {

    List<Store> findByDeletedFalseOrderByCodeAsc();

    List<Store> findByActiveTrueAndDeletedFalseOrderByCodeAsc();

    Optional<Store> findByIdAndDeletedFalse(Long id);

    boolean existsByIdAndDeletedFalse(Long id);

    Optional<Store> findByCodeAndDeletedFalse(String code);

    boolean existsByCodeAndDeletedFalse(String code);

    /** Tìm theo mã trên MỌI dòng, kể cả cơ sở đã xoá mềm (uk_stores_code phủ cả dòng đã xoá). */
    Optional<Store> findByCode(String code);

    /** Mã đã bị chiếm bởi bất kỳ dòng nào, kể cả cơ sở đã xoá mềm (khớp uk_stores_code). */
    boolean existsByCode(String code);
}
