package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.feedback.FeedbackRequest;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.security.ClientIpResolver;
import com.banhmyking.banhmyking.security.SecurityUtils;
import com.banhmyking.banhmyking.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Gửi phản hồi — công khai, đăng nhập tuỳ chọn (spec D §5). */
@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
@Tag(name = "Feedbacks", description = "Gửi phản hồi công khai")
public class FeedbackController {

    public static final String SUBMITTED = "Cảm ơn bạn! Phản hồi đã được gửi tới cửa hàng.";

    private final FeedbackService feedbackService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping
    @Operation(summary = "Gửi phản hồi",
            description = "orderCode chỉ dùng khi đăng nhập và là chủ đơn (không thì 404). Tối đa 5 lần/giờ/IP → 429. "
                    + "Có header Bearer nhưng token hết hạn/không hợp lệ → 401 để frontend làm mới token.")
    public ResponseEntity<ApiResponse<Void>> submit(@RequestBody FeedbackRequest request,
                                                    @AuthenticationPrincipal UserDetails principal,
                                                    HttpServletRequest httpRequest) {
        String authorization = httpRequest.getHeader("Authorization");
        if (principal == null && authorization != null && authorization.startsWith("Bearer ")) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "Phiên đăng nhập đã hết hạn, vui lòng thử lại");
        }
        Long senderId = principal == null ? null : SecurityUtils.requireUserId(principal);
        feedbackService.submit(senderId, request, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.ok(ApiResponse.ok(SUBMITTED));
    }
}
