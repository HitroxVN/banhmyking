package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 1 user · 1 role. */
@Getter
@Setter
@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_email", columnList = "email", unique = true))
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoleName role = RoleName.CUSTOMER;

    /** Khoá tài khoản — ADMIN khoá/mở, chặn cả login lẫn token cũ. */
    @Column(name = "is_banned", nullable = false)
    private boolean banned = false;

    /** Bắt buộc xác thực email trước khi login. User mới = false, user do ADMIN/seed tạo = true. */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /** SHA-256 của token xác thực đang hiệu lực — không lưu token thô. */
    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_token_expires_at")
    private LocalDateTime verificationTokenExpiresAt;

    /** SHA-256 của token đặt lại mật khẩu đang hiệu lực. Tách khỏi token xác thực email. */
    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;

    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    /** Avatar dạng URL. */
    @Column(name = "image", length = 255)
    private String image;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Address> addresses = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Review> reviews = new ArrayList<>();
}
