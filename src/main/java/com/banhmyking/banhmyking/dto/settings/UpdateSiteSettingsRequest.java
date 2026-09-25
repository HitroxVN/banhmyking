package com.banhmyking.banhmyking.dto.settings;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Cả payload là một map key → value vì cấu hình lưu dạng key/value.
 * Key hợp lệ nằm ở {@code SiteSettingKeys.DEFAULTS}, value bị chặn độ dài ở service.
 */
public record UpdateSiteSettingsRequest(
        @NotNull(message = "Thiếu dữ liệu cấu hình")
        Map<String, String> settings
) {
}
