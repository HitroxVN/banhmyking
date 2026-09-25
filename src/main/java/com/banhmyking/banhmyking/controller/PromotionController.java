package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.promotion.CreatePromotionRequest;
import com.banhmyking.banhmyking.dto.promotion.PromotionResponse;
import com.banhmyking.banhmyking.dto.promotion.UpdatePromotionRequest;
import com.banhmyking.banhmyking.dto.promotion.ValidatePromotionRequest;
import com.banhmyking.banhmyking.service.PromotionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Promotion", description = "APIs quản lý mã giảm giá & Atomic Redemption (Sprint 6 - PROMO-01)")
public class PromotionController {

    private final PromotionService promotionService;

    @Operation(summary = "Kiểm tra mã giảm giá (CUSTOMER)", description = "Kiểm tra mã giảm giá còn hiệu lực, đạt điều kiện đơn hàng và tính số tiền được giảm")
    @PostMapping("/promotions/validate")
    public ResponseEntity<ApiResponse<PromotionResponse>> validatePromotion(
            @Valid @RequestBody ValidatePromotionRequest request) {
        PromotionResponse response = promotionService.validatePromotion(request);
        return ResponseEntity.ok(ApiResponse.ok("Mã giảm giá hợp lệ", response));
    }

    @Operation(summary = "Test Atomic Redemption (PROMO-01)", description = "Chạy thử nghiệm trừ lượt sử dụng mã giảm giá bằng UPDATE nguyên tử trên DB. Trả lỗi nếu hết lượt.")
    @PostMapping("/promotions/{id}/redeem-atomic")
    public ResponseEntity<ApiResponse<PromotionResponse>> testRedeemAtomic(
            @PathVariable Long id) {
        PromotionResponse response = promotionService.testRedeemAtomic(id);
        return ResponseEntity.ok(ApiResponse.ok("Trừ lượt mã giảm giá nguyên tử (Atomic Redemption) thành công", response));
    }

    @Operation(summary = "Tạo mã giảm giá mới (ADMIN)", description = "Admin tạo mới mã giảm giá với số lượt dùng và điều kiện")
    @PostMapping("/admin/promotions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromotionResponse>> createPromotion(
            @Valid @RequestBody CreatePromotionRequest request) {
        PromotionResponse response = promotionService.createPromotion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo mã giảm giá thành công", response));
    }

    @Operation(summary = "Danh sách mã giảm giá (ADMIN)", description = "Lấy toàn bộ danh sách mã giảm giá trong hệ thống")
    @GetMapping("/admin/promotions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<PromotionResponse>>> getAllPromotions() {
        List<PromotionResponse> response = promotionService.getAllPromotions();
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách mã giảm giá thành công", response));
    }

    @Operation(summary = "Chi tiết mã giảm giá (ADMIN)", description = "Xem thông tin chi tiết mã giảm giá theo ID")
    @GetMapping("/admin/promotions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromotionResponse>> getPromotionById(
            @PathVariable Long id) {
        PromotionResponse response = promotionService.getPromotionById(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin mã giảm giá thành công", response));
    }

    @Operation(summary = "Cập nhật mã giảm giá (ADMIN)", description = "Cập nhật thông tin mã giảm giá theo ID")
    @PutMapping("/admin/promotions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PromotionResponse>> updatePromotion(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePromotionRequest request) {
        PromotionResponse response = promotionService.updatePromotion(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật mã giảm giá thành công", response));
    }

    @Operation(summary = "Xóa mã giảm giá (ADMIN)", description = "Vô hiệu hóa hoặc xóa mã giảm giá theo ID")
    @DeleteMapping("/admin/promotions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deletePromotion(
            @PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa mã giảm giá thành công"));
    }
}
