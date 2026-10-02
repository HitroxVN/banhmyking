package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.enums.JobStatus;

/** Đóng/mở nhanh tin tuyển dụng. */
public record JobStatusRequest(JobStatus status) {
}
