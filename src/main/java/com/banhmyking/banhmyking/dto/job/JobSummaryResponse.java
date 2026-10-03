package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobPosting;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.enums.EmploymentType;
import java.time.LocalDate;
import java.util.List;

/** Thẻ tin trên trang /tuyen-dung. stores = cơ sở đang nhận hồ sơ. */
public record JobSummaryResponse(
        Long id,
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        boolean chainWide,
        List<StoreRef> stores,
        boolean acceptingApplications
) {
    public static JobSummaryResponse of(JobPosting job, List<Store> receivingStores, boolean accepting) {
        return new JobSummaryResponse(job.getId(), job.getTitle(), job.getSlug(), job.getEmploymentType(),
                job.getSalaryText(), job.getHeadcount(), job.getDeadline(), job.isChainWide(),
                receivingStores.stream().map(StoreRef::from).toList(), accepting);
    }
}
