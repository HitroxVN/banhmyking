package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.job.JobDetailResponse;
import com.banhmyking.banhmyking.dto.job.JobSummaryResponse;
import com.banhmyking.banhmyking.service.JobPostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Tin tuyển dụng công khai (spec D §4)")
public class JobController {

    private final JobPostingService jobPostingService;

    @GetMapping
    @Operation(summary = "Tin đang tuyển", description = "storeId: chỉ tin tuyển ở cơ sở đó hoặc tuyển toàn chuỗi.")
    public ResponseEntity<ApiResponse<List<JobSummaryResponse>>> list(@RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin tuyển dụng thành công", jobPostingService.listOpen(storeId)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Chi tiết tin", description = "Tin đóng/hết hạn vẫn xem được, acceptingApplications = false.")
    public ResponseEntity<ApiResponse<JobDetailResponse>> detail(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin tuyển dụng thành công", jobPostingService.getBySlug(slug)));
    }
}
