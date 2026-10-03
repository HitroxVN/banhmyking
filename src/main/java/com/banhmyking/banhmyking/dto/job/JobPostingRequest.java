package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.enums.EmploymentType;
import com.banhmyking.banhmyking.enums.JobStatus;
import java.time.LocalDate;
import java.util.List;

/** Tạo/sửa tin tuyển dụng (ADMIN). storeIds rỗng/null = tuyển toàn chuỗi; status null = OPEN. */
public record JobPostingRequest(
        String title,
        String slug,
        EmploymentType employmentType,
        String salaryText,
        Integer headcount,
        LocalDate deadline,
        String description,
        JobStatus status,
        List<Long> storeIds
) {
}
