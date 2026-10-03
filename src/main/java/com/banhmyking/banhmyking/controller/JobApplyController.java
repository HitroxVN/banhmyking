package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.job.JobApplicationForm;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.service.JobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Nộp hồ sơ công khai (spec D §4). Mọi trường required = false để thiếu trường ra câu lỗi tiếng Việt
 * từ service thay vì lỗi 500. Thành công thật và ô bẫy bot trả cùng một phản hồi.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Job applications", description = "Nộp hồ sơ ứng tuyển công khai")
public class JobApplyController {

    public static final String SUBMITTED = "Đã gửi hồ sơ ứng tuyển. Cửa hàng sẽ liên hệ với bạn sớm.";

    private final JobApplicationService jobApplicationService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping(value = "/{slug}/applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nộp hồ sơ ứng tuyển",
            description = "multipart: storeId, fullName, phone, email, message, cv (PDF/JPG/PNG ≤ 5MB, tuỳ chọn). "
                    + "Tối đa 5 lần/giờ/IP (chung với phản hồi) → 429.")
    public ResponseEntity<ApiResponse<Void>> apply(
            @PathVariable String slug,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String website,
            @RequestPart(value = "cv", required = false) MultipartFile cv,
            HttpServletRequest request) {
        jobApplicationService.submit(slug, new JobApplicationForm(storeId, fullName, phone, email, message, website),
                cv, clientIpResolver.resolve(request));
        return ResponseEntity.ok(ApiResponse.ok(SUBMITTED));
    }
}
