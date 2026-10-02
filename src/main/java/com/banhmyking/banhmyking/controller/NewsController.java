package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.news.NewsDetailResponse;
import com.banhmyking.banhmyking.dto.news.NewsSummaryResponse;
import com.banhmyking.banhmyking.service.NewsService;
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
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@Tag(name = "News", description = "Tin tức công khai (spec D §3)")
public class NewsController {

    private final NewsService newsService;

    @GetMapping
    @Operation(summary = "Bài đã đăng", description = "Ghim trước rồi mới nhất trước; không trả nội dung.")
    public ResponseEntity<ApiResponse<PageResponse<NewsSummaryResponse>>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "9") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách tin tức thành công",
                newsService.listPublished(page, size)));
    }

    @GetMapping("/latest")
    @Operation(summary = "Tin mới nhất cho trang chủ")
    public ResponseEntity<ApiResponse<List<NewsSummaryResponse>>> latest(@RequestParam(defaultValue = "3") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tin mới thành công", newsService.latest(limit)));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Chi tiết bài + 3 bài liên quan", description = "Bài nháp/hẹn giờ/đã xoá → 404.")
    public ResponseEntity<ApiResponse<NewsDetailResponse>> detail(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy bài viết thành công", newsService.getPublished(slug)));
    }
}
