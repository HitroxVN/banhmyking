package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.CvFile;
import com.banhmyking.banhmyking.dto.job.JobApplicationResponse;
import com.banhmyking.banhmyking.dto.job.UpdateApplicationRequest;
import com.banhmyking.banhmyking.enums.ApplicationStatus;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.JobApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Xử lý hồ sơ ứng tuyển: MANAGER cơ sở mình, ADMIN mọi cơ sở (spec D §4, §6). */
@RestController
@RequestMapping("/api/v1/job-applications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@Tag(name = "Job applications (quản lý)", description = "Hồ sơ ứng tuyển theo phạm vi cơ sở")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @GetMapping
    @Operation(summary = "Danh sách hồ sơ", description = "MANAGER bị ép storeId = cơ sở mình; cơ sở khác → 404.")
    public ResponseEntity<ApiResponse<PageResponse<JobApplicationResponse>>> list(
            @RequestParam(required = false) Long jobId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách hồ sơ thành công", jobApplicationService.search(
                SecurityUtils.requireUserId(principal), jobId, storeId, status, page, size)));
    }

    @GetMapping("/count-new")
    @Operation(summary = "Số hồ sơ NEW trong phạm vi (huy hiệu menu)")
    public ResponseEntity<ApiResponse<Long>> countNew(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(jobApplicationService.countNew(SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> get(@PathVariable Long id,
                                                                   @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy hồ sơ thành công",
                jobApplicationService.get(SecurityUtils.requireUserId(principal), id)));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Đổi trạng thái / ghi chú nội bộ")
    public ResponseEntity<ApiResponse<JobApplicationResponse>> update(@PathVariable Long id,
                                                                      @RequestBody UpdateApplicationRequest request,
                                                                      @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật hồ sơ",
                jobApplicationService.update(SecurityUtils.requireUserId(principal), id, request)));
    }

    @GetMapping("/{id}/cv")
    @Operation(summary = "Tải CV", description = "Chỉ qua API này sau kiểm quyền; luôn tải xuống (attachment).")
    public ResponseEntity<Resource> downloadCv(@PathVariable Long id, @AuthenticationPrincipal UserDetails principal) {
        CvFile cv = jobApplicationService.loadCv(SecurityUtils.requireUserId(principal), id);
        String fileName = cv.originalName() == null ? "cv" : cv.originalName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(cv.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM
                        : MediaType.parseMediaType(cv.contentType()))
                .body(cv.resource());
    }
}
