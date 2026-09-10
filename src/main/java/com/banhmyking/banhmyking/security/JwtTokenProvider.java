package com.banhmyking.banhmyking.security;

import com.banhmyking.banhmyking.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Tạo và xác thực JWT access token (HS256).
 * Refresh token là UUID opaque — caller hash SHA-256 trước khi lưu DB.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiryMs;

    public JwtTokenProvider(JwtProperties props) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(props.secret());
            if (keyBytes.length < 32) {
                keyBytes = props.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            keyBytes = props.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiryMs = props.accessTokenExpiryMs();
    }

    /** Subject = userId (String), claim "role" = RoleName.name(). */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + accessTokenExpiryMs);
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * Tạo opaque refresh token (UUID v4).
     * Caller chịu trách nhiệm SHA-256 hash trước khi lưu vào DB.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * Parse và xác thực JWT — ném {@link JwtException} nếu lỗi (expired, tampered…).
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Lấy userId từ access token (subject). */
    public Long extractUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }
}
