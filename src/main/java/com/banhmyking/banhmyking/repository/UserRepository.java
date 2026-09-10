package com.banhmyking.banhmyking.repository;

import com.banhmyking.banhmyking.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndDeletedFalse(String email);

    boolean existsByEmail(String email);
}
