package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.settings.UpdateSiteSettingsRequest;
import com.banhmyking.banhmyking.service.SiteSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Site Setting", description = "Cấu hình nội dung website do admin sửa")
public class SiteSettingController {

    private final SiteSettingService siteSettingService;

    @Operation(summary = "Đọc cấu hình nội dung website (công khai)",
            description = "Dùng cho cả trang chưa đăng nhập (trang login) nên mở công khai. "
                    + "Luôn trả đủ mọi key, key chưa lưu trả về giá trị mặc định.")
    @GetMapping("/site-settings")
    public ResponseEntity<ApiResponse<Map<String, String>>> getSiteSettings() {
        return ResponseEntity.ok(ApiResponse.ok(siteSettingService.getPublicSettings()));
    }

    @Operation(summary = "Cập nhật cấu hình nội dung website (ADMIN)",
            description = "Cập nhật một phần: chỉ ghi những key có trong payload, key vắng mặt giữ nguyên.")
    @PutMapping("/admin/site-settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> updateSiteSettings(
            @Valid @RequestBody UpdateSiteSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Đã lưu cấu hình trang web",
                siteSettingService.updateSettings(request.settings())));
    }

    @Operation(summary = "Tải ảnh banner trang chủ lên (ADMIN)")
    @PostMapping(value = "/admin/site-settings/hero-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> uploadHeroImage(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Tải ảnh banner lên thành công",
                siteSettingService.uploadHeroImage(file)));
    }
}
