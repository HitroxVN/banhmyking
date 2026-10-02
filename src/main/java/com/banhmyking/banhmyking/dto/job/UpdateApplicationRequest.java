package com.banhmyking.banhmyking.dto.job;

import com.banhmyking.banhmyking.enums.ApplicationStatus;

/** PATCH hồ sơ: trường null = giữ nguyên; internalNote rỗng = xoá ghi chú. */
public record UpdateApplicationRequest(ApplicationStatus status, String internalNote) {
}
