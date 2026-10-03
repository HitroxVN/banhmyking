package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import java.time.LocalDate;
import java.util.List;

/** Trang /tuyen-dung/:slug — tin đóng/hết hạn vẫn xem được với acceptingApplications = false. */
public record JobDetailResponse(
        Long id,
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        String description,
        boolean chainWide,
        List<StoreRef> stores,
        boolean acceptingApplications
) {
    public static JobDetailResponse of(JobPosting job, List<Store> receivingStores, boolean accepting) {
        return new JobDetailResponse(job.getId(), job.getTitle(), job.getSlug(), job.getEmploymentType(),
                job.getSalaryText(), job.getHeadcount(), job.getDeadline(), job.getDescription(),
                job.isChainWide(), receivingStores.stream().map(StoreRef::from).toList(),
                accepting);
    }
}
