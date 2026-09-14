package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndDeletedFalse(Long id);

    /**
     * khóa dòng user khi thao tác lazy-init giỏ hàng — hai request cùng user chạy song
     * song sẽ xếp hàng thay vì cùng thấy cart trống và cùng INSERT (va chạm unique user_id).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    long countByRoleAndDeletedFalseAndBannedFalse(RoleName role);

    long countByRoleAndDeletedFalse(RoleName role);

    long countByDeletedFalse();

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
