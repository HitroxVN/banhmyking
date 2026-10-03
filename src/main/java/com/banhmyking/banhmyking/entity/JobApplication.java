package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Hồ sơ ứng tuyển (spec D §2.4). CV nằm trong thư mục riêng, chỉ lưu khoá file ở đây. */
@Getter
@Setter
@Entity
@Table(name = "job_applications")
public class JobApplication extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    /** Cơ sở ứng viên muốn làm — quyết định MANAGER nào thấy hồ sơ. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 2000)
    private String message;

    @Column(name = "cv_file_key", length = 100)
    private String cvFileKey;

    @Column(name = "cv_original_name", length = 255)
    private String cvOriginalName;

    @Column(name = "cv_content_type", length = 100)
    private String cvContentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.NEW;

    @Column(name = "internal_note", length = 2000)
    private String internalNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    public boolean hasCv() {
        return cvFileKey != null;
    }
}
