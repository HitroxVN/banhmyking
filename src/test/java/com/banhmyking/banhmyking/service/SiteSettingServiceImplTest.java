package com.banhmyking.banhmyking.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.banhmyking.banhmyking.entity.SiteSetting;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.SiteSettingRepository;
import com.banhmyking.banhmyking.service.impl.SiteSettingServiceImpl;

@ExtendWith(MockitoExtension.class)
class SiteSettingServiceImplTest {

    @Mock
    private SiteSettingRepository siteSettingRepository;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private SiteSettingServiceImpl siteSettingService;

    private static SiteSetting row(String key, String value) {
        return SiteSetting.builder().settingKey(key).settingValue(value).build();
    }

    private static ArrayList<SiteSetting> rows(SiteSetting... settings) {
        return new ArrayList<>(List.of(settings));
    }

    // ─── Đọc ──────────────────────────────────────────────────────────────────────

    @Test
    void getPublicSettingsReturnsDefaultsWhenTableEmpty() {
        when(siteSettingRepository.findAll()).thenReturn(new ArrayList<>());

        Map<String, String> settings = siteSettingService.getPublicSettings();

        assertThat(settings).isEqualTo(SiteSettingKeys.DEFAULTS);
        assertThat(settings.get(SiteSettingKeys.SITE_NAME)).isEqualTo("Bánh Mỳ King");
    }

    @Test
    void getPublicSettingsLetsStoredValueWinOverDefault() {
        when(siteSettingRepository.findAll())
                .thenReturn(rows(row(SiteSettingKeys.SITE_NAME, "Bánh Mỳ Queen")));

        Map<String, String> settings = siteSettingService.getPublicSettings();

        assertThat(settings.get(SiteSettingKeys.SITE_NAME)).isEqualTo("Bánh Mỳ Queen");
        // Key không có trong DB vẫn trả mặc định, không bị thiếu
        assertThat(settings.get(SiteSettingKeys.TAGLINE))
                .isEqualTo(SiteSettingKeys.DEFAULTS.get(SiteSettingKeys.TAGLINE));
    }

    @Test
    void getPublicSettingsIgnoresStaleKeyNotInDefaults() {
        when(siteSettingRepository.findAll()).thenReturn(rows(row("khoaCuKhongConDung", "x")));

        assertThat(siteSettingService.getPublicSettings()).doesNotContainKey("khoaCuKhongConDung");
    }

    // ─── Ghi ──────────────────────────────────────────────────────────────────────

    @Test
    void updateSettingsRejectsUnknownKey() {
        assertThatThrownBy(() -> siteSettingService.updateSettings(Map.of("khongTonTai", "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("khongTonTai");

        verify(siteSettingRepository, never()).save(any());
    }

    @Test
    void updateSettingsRejectsOverLongValue() {
        String tooLong = "x".repeat(SiteSettingKeys.MAX_VALUE_LENGTH + 1);

        assertThatThrownBy(() -> siteSettingService.updateSettings(Map.of(SiteSettingKeys.TAGLINE, tooLong)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quá dài");

        verify(siteSettingRepository, never()).save(any());
    }

    @Test
    void updateSettingsTrimsValueAndLeavesAbsentKeyAlone() {
        when(siteSettingRepository.findAll()).thenReturn(rows(
                row(SiteSettingKeys.SITE_NAME, "Bánh Mỳ King"),
                row(SiteSettingKeys.TAGLINE, "Vỏ giòn · nhân đầy")));

        siteSettingService.updateSettings(Map.of(SiteSettingKeys.SITE_NAME, "  Bánh Mỳ Queen  "));

        ArgumentCaptor<SiteSetting> captor = ArgumentCaptor.forClass(SiteSetting.class);
        verify(siteSettingRepository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo(SiteSettingKeys.SITE_NAME);
        assertThat(captor.getValue().getSettingValue()).isEqualTo("Bánh Mỳ Queen");

        // TAGLINE không nằm trong payload → không bị đụng tới
        verify(siteSettingRepository, never())
                .save(argThat(row -> SiteSettingKeys.TAGLINE.equals(row.getSettingKey())));
    }

    @Test
    void updateSettingsInsertsRowForKeyNotStoredYet() {
        when(siteSettingRepository.findAll()).thenReturn(new ArrayList<>());

        siteSettingService.updateSettings(Map.of(SiteSettingKeys.CONTACT_PHONE, "0901234567"));

        ArgumentCaptor<SiteSetting> captor = ArgumentCaptor.forClass(SiteSetting.class);
        verify(siteSettingRepository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo(SiteSettingKeys.CONTACT_PHONE);
        assertThat(captor.getValue().getSettingValue()).isEqualTo("0901234567");
    }

    // ─── Ảnh banner ───────────────────────────────────────────────────────────────

    @Test
    void uploadHeroImageStoresIntoBannersDirAndSavesUrl() {
        MockMultipartFile file = new MockMultipartFile("file", "banner.png", "image/png", new byte[]{1, 2, 3});
        when(fileStorageService.storeImage(file, FileStorageService.SITE_DIR))
                .thenReturn("/uploads/banners/abc.png");
        when(siteSettingRepository.findAll()).thenReturn(new ArrayList<>());

        assertThat(siteSettingService.uploadHeroImage(file)).isEqualTo("/uploads/banners/abc.png");

        ArgumentCaptor<SiteSetting> captor = ArgumentCaptor.forClass(SiteSetting.class);
        verify(siteSettingRepository).save(captor.capture());
        assertThat(captor.getValue().getSettingKey()).isEqualTo(SiteSettingKeys.HERO_IMAGE_URL);
        assertThat(captor.getValue().getSettingValue()).isEqualTo("/uploads/banners/abc.png");
    }

    @Test
    void updateSettingsDeletesOldBannerFileWhenImageChanges() {
        when(siteSettingRepository.findAll()).thenReturn(rows(
                row(SiteSettingKeys.HERO_IMAGE_URL, "/uploads/banners/cu.png")));

        siteSettingService.updateSettings(Map.of(SiteSettingKeys.HERO_IMAGE_URL, "/uploads/banners/moi.png"));

        verify(fileStorageService).deleteImage("/uploads/banners/cu.png", FileStorageService.SITE_DIR);
    }

    @Test
    void updateSettingsKeepsBannerFileWhenImageUnchanged() {
        when(siteSettingRepository.findAll()).thenReturn(rows(
                row(SiteSettingKeys.HERO_IMAGE_URL, "/uploads/banners/cu.png")));

        siteSettingService.updateSettings(Map.of(SiteSettingKeys.TAGLINE, "Vỏ giòn · nhân đầy"));

        verify(fileStorageService, never()).deleteImage(any(), any());
    }
}
