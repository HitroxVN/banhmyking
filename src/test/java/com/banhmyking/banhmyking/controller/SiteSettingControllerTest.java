package com.banhmyking.banhmyking.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.SiteSettingKeys;
import com.banhmyking.banhmyking.service.SiteSettingService;

/**
 * Standalone MockMvc nên KHÔNG kiểm tra được phân quyền (@PreAuthorize / SecurityConfig) —
 * phần 403 của CUSTOMER được xác nhận bằng curl ở bước E2E.
 */
@ExtendWith(MockitoExtension.class)
class SiteSettingControllerTest {

    @Mock
    SiteSettingService siteSettingService;

    @InjectMocks
    SiteSettingController siteSettingController;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(siteSettingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static Map<String, String> fullSettings() {
        return new LinkedHashMap<>(SiteSettingKeys.DEFAULTS);
    }

    @Test
    void getSiteSettings_publicWithoutToken_returnsAllKeys() throws Exception {
        when(siteSettingService.getPublicSettings()).thenReturn(fullSettings());

        mockMvc.perform(get("/api/v1/site-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data." + SiteSettingKeys.SITE_NAME).value("Bánh Mỳ King"))
                .andExpect(jsonPath("$.data." + SiteSettingKeys.HERO_IMAGE_URL).value(""));
    }

    @Test
    void updateSiteSettings_returnsSavedSettings() throws Exception {
        Map<String, String> saved = fullSettings();
        saved.put(SiteSettingKeys.SITE_NAME, "Bánh Mỳ Queen");
        when(siteSettingService.updateSettings(anyMap())).thenReturn(saved);

        mockMvc.perform(put("/api/v1/admin/site-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"settings\":{\"siteName\":\"Bánh Mỳ Queen\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu cấu hình trang web"))
                .andExpect(jsonPath("$.data." + SiteSettingKeys.SITE_NAME).value("Bánh Mỳ Queen"));
    }

    @Test
    void updateSiteSettings_missingSettingsMap_returns400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/site-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void uploadHeroImage_returnsStoredUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", new byte[]{1, 2, 3});
        when(siteSettingService.uploadHeroImage(any())).thenReturn("/uploads/banners/abc.png");

        mockMvc.perform(multipart("/api/v1/admin/site-settings/hero-image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tải ảnh banner lên thành công"))
                .andExpect(jsonPath("$.data").value("/uploads/banners/abc.png"));
    }
}
