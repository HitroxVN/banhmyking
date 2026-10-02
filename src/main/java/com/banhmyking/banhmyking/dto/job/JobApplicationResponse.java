package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.entity.JobApplication;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import java.time.LocalDateTime;

/** Hồ sơ cho màn quản lý — không lộ khoá file CV, chỉ báo có/không và tên gốc. */
public record JobApplicationResponse(
        Long id,
        Long jobId,
        String jobTitle,
        String jobSlug,
        Long storeId,
        String storeName,
        String fullName,
        String phone,
        String email,
        String message,
        boolean hasCv,
        String cvOriginalName,
        ApplicationStatus status,
        String internalNote,
        String handledByName,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {
    public static JobApplicationResponse from(JobApplication app) {
        return new JobApplicationResponse(app.getId(), app.getJobPosting().getId(), app.getJobPosting().getTitle(),
                app.getJobPosting().getSlug(), app.getStore().getId(), app.getStore().getName(), app.getFullName(),
                app.getPhone(), app.getEmail(), app.getMessage(), app.hasCv(), app.getCvOriginalName(),
                app.getStatus(), app.getInternalNote(),
                app.getHandledBy() == null ? null : app.getHandledBy().getFullName(), app.getHandledAt(),
                app.getCreatedAt());
    }
}
