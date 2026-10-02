package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.AdminNewsResponse;
import com.banhmyking.banhmyking.dto.news.NewsRequest;
import com.banhmyking.banhmyking.enums.NewsDisplayState;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.NewsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/news")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin news", description = "Soạn tin tức (chỉ ADMIN)")
public class AdminNewsController {

    private final NewsService newsService;

    @GetMapping
    @Operation(summary = "Danh sách bài", description = "status = DRAFT | SCHEDULED | PUBLISHED (suy ra từ published_at).")
    public ResponseEntity<ApiResponse<PageResponse<AdminNewsResponse>>> list(
            @RequestParam(required = false) NewsDisplayState status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách bài viết thành công",
                newsService.searchAdmin(status, keyword, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminNewsResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy bài viết thành công", newsService.getAdmin(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AdminNewsResponse>> create(@RequestBody NewsRequest request,
                                                                 @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã tạo bài viết",
                newsService.create(SecurityUtils.requireUserId(principal), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminNewsResponse>> update(@PathVariable Long id,
                                                                 @RequestBody NewsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật bài viết", newsService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        newsService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Đã xoá bài viết"));
    }
}
