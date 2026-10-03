package com.banhmyking.banhmyking.dto.job;

import org.springframework.core.io.Resource;

/** File CV đã qua kiểm quyền, sẵn sàng trả về dạng tải xuống. */
public record CvFile(Resource resource, String originalName, String contentType) {
}
