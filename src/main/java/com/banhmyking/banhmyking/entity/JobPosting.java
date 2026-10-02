package com.banhmyking.banhmyking.entity;

import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/** Tin tuyển dụng (spec D §2.2–2.3). Không gắn cơ sở nào = tuyển toàn chuỗi. */
@Getter
@Setter
@Entity
@Table(name = "job_postings")
public class JobPosting extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 220)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType;

    @Column(name = "salary_text", length = 100)
    private String salaryText;

    private Integer headcount;

    /** NULL = không hạn; hết hạn sau cuối ngày deadline. */
    private LocalDate deadline;

    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.OPEN;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "job_posting_stores",
            joinColumns = @JoinColumn(name = "job_posting_id"),
            inverseJoinColumns = @JoinColumn(name = "store_id"))
    @BatchSize(size = 50)
    private Set<Store> stores = new HashSet<>();

    public boolean isChainWide() {
        return stores.isEmpty();
    }

    public boolean isExpiredOn(LocalDate today) {
        return deadline != null && today.isAfter(deadline);
    }

    /** Còn nhận hồ sơ: OPEN, chưa xoá, chưa quá cuối ngày deadline (spec D §4). */
    public boolean acceptsApplicationsOn(LocalDate today) {
        return !deleted && status == JobStatus.OPEN && !isExpiredOn(today);
    }
}
