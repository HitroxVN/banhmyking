package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.job.AdminJobResponse;
import com.banhmyking.banhmyking.dto.job.JobPostingRequest;
import com.banhmyking.banhmyking.dto.job.JobStatusRequest;
import com.banhmyking.banhmyking.enums.JobStatus;
import com.banhmyking.banhmyking.service.JobPostingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin jobs", description = "Soạn tin tuyển dụng (chỉ ADMIN)")
public class AdminJobController {

    private final JobPostingService jobPostingService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<AdminJobResponse>>> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tin tuyển dụng thành công",
                jobPostingService.searchAdmin(status, keyword, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminJobResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin tuyển dụng thành công", jobPostingService.getAdmin(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminJobResponse>> create(@RequestBody JobPostingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Đã tạo tin tuyển dụng", jobPostingService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminJobResponse>> update(@PathVariable Long id,
                                                                @RequestBody JobPostingRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật tin tuyển dụng", jobPostingService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<AdminJobResponse>> setStatus(@PathVariable Long id,
                                                                   @RequestBody JobStatusRequest request) {
        AdminJobResponse job = jobPostingService.setStatus(id, request.status());
        return ResponseEntity.ok(ApiResponse.ok(
                job.status() == JobStatus.OPEN ? "Đã mở lại tin tuyển dụng" : "Đã đóng tin tuyển dụng", job));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        jobPostingService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xoá tin tuyển dụng"));
    }
}
