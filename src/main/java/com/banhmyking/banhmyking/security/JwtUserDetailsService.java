package com.banhmyking.banhmyking.security;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Load UserDetails by userId (String) — dùng cho JwtAuthenticationFilter.
 * Tách với CustomUserDetailsService (load by email) tránh nhập nhằng.
 */
@Service
@RequiredArgsConstructor
public class JwtUserDetailsService {

    private final UserRepository userRepository;

    public UserDetails loadById(Long userId) {
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user id: " + userId));

        return new org.springframework.security.core.userdetails.User(
                user.getId().toString(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}
