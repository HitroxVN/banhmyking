package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndDeletedFalse(Long id);

    /** Đếm ADMIN đang hoạt động (chưa xoá, chưa khoá) — cho guard ADMIN cuối cùng. */
    long countByRoleAndDeletedFalseAndBannedFalse(RoleName role);

    /** Tìm user còn hoạt động theo filter tuỳ ý (null = bỏ qua filter). */
    @Query("""
            SELECT u FROM User u
            WHERE u.deleted = false
              AND (:role IS NULL OR u.role = :role)
              AND (:banned IS NULL OR u.banned = :banned)
              AND (:keyword IS NULL
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<User> searchUsers(@Param("role") RoleName role,
                           @Param("banned") Boolean banned,
                           @Param("keyword") String keyword,
                           Pageable pageable);
}
