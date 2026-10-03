package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackResponse;
import com.banhmyking.banhmyking.dto.feedback.UpdateFeedbackRequest;
import com.banhmyking.banhmyking.enums.FeedbackStatus;
import com.banhmyking.banhmyking.enums.FeedbackType;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

/** Xử lý phản hồi: MANAGER cơ sở mình, ADMIN tất cả (kể cả chung toàn chuỗi). */
@RestController
@RequestMapping("/api/v1/admin/feedbacks")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
@Tag(name = "Admin feedbacks", description = "Hộp phản hồi theo phạm vi cơ sở")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    @Operation(summary = "Danh sách phản hồi", description = "MANAGER bị ép storeId = cơ sở mình; cơ sở khác → 404.")
    public ResponseEntity<ApiResponse<PageResponse<FeedbackResponse>>> list(
            @RequestParam(required = false) FeedbackType type,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) FeedbackStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách phản hồi thành công", feedbackService.search(
                SecurityUtils.requireUserId(principal), type, storeId, status, page, size)));
    }

    @GetMapping("/count-new")
    public ResponseEntity<ApiResponse<Long>> countNew(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(feedbackService.countNew(SecurityUtils.requireUserId(principal))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> get(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy phản hồi thành công",
                feedbackService.get(SecurityUtils.requireUserId(principal), id)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<FeedbackResponse>> update(@PathVariable Long id,
                                                                @RequestBody UpdateFeedbackRequest request,
                                                                @AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok("Đã cập nhật phản hồi",
                feedbackService.update(SecurityUtils.requireUserId(principal), id, request)));
    }
}
