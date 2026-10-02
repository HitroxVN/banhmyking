package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/** Tin cho màn quản trị. stores = cơ sở đã cấu hình (rỗng = toàn chuỗi). */
public record AdminJobResponse(
        Long id,
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        String description,
        JobStatus status,
        boolean expired,
        boolean chainWide,
        List<StoreRef> stores,
        LocalDateTime createdAt
) {
    public static AdminJobResponse from(JobPosting job, LocalDate today) {
        return new AdminJobResponse(job.getId(), job.getTitle(), job.getSlug(), job.getEmploymentType(),
                job.getSalaryText(), job.getHeadcount(), job.getDeadline(), job.getDescription(), job.getStatus(),
                job.isExpiredOn(today), job.isChainWide(),
                job.getStores().stream().sorted(Comparator.comparing(Store::getCode)).map(StoreRef::from).toList(),
                job.getCreatedAt());
    }
}
