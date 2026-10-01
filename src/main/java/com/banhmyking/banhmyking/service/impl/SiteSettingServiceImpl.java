package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.entity.SiteSetting;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.SiteSettingRepository;
import com.banhmyking.banhmyking.service.FileStorageService;
import com.banhmyking.banhmyking.service.SiteSettingKeys;
import com.banhmyking.banhmyking.service.SiteSettingService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class SiteSettingServiceImpl implements SiteSettingService {

    private final SiteSettingRepository siteSettingRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getPublicSettings() {
        Map<String, String> merged = new LinkedHashMap<>(SiteSettingKeys.DEFAULTS);

        // Ghi đè lên bản mặc định chứ không thêm key mới: response luôn đúng bằng
        // tập key trong DEFAULTS, một dòng cấu hình cũ còn sót lại không làm phình response.
        siteSettingRepository.findAll().stream()
                .filter(row -> row.getSettingValue() != null)
                .filter(row -> merged.containsKey(row.getSettingKey()))
                .forEach(row -> merged.put(row.getSettingKey(), row.getSettingValue()));

        return merged;
    }

    @Override
    @Transactional
    public Map<String, String> updateSettings(Map<String, String> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return getPublicSettings();
        }

        // Kiểm tra hết trước khi ghi: một key sai nằm giữa payload không để lại cập nhật nửa vời.
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : incoming.entrySet()) {
            String key = entry.getKey();
            if (!SiteSettingKeys.DEFAULTS.containsKey(key)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khoá cấu hình không hợp lệ: " + key);
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.length() > SiteSettingKeys.MAX_VALUE_LENGTH) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Giá trị của \"" + key + "\" quá dài (tối đa "
                                + SiteSettingKeys.MAX_VALUE_LENGTH + " ký tự)");
            }
            values.put(key, value);
        }

        String previousHeroImage = getPublicSettings().get(SiteSettingKeys.HERO_IMAGE_URL);

        Map<String, SiteSetting> rows = new LinkedHashMap<>();
        siteSettingRepository.findAll().forEach(row -> rows.put(row.getSettingKey(), row));

        for (Map.Entry<String, String> entry : values.entrySet()) {
            SiteSetting row = rows.get(entry.getKey());
            if (row == null) {
                row = SiteSetting.builder().settingKey(entry.getKey()).build();
            }
            row.setSettingValue(entry.getValue());
            siteSettingRepository.save(row);
        }

        // Chỉ xoá file ảnh cũ khi admin thực sự đổi sang ảnh khác; xoá ảnh ngoài /uploads/banners/
        // đã bị FileStorageService chặn sẵn.
        if (values.containsKey(SiteSettingKeys.HERO_IMAGE_URL)
                && !values.get(SiteSettingKeys.HERO_IMAGE_URL).equals(previousHeroImage)) {
            fileStorageService.deleteImage(previousHeroImage, FileStorageService.SITE_DIR);
        }

        return getPublicSettings();
    }

    @Override
    @Transactional
    public String uploadHeroImage(MultipartFile file) {
        String publicUrl = fileStorageService.storeImage(file, FileStorageService.SITE_DIR);
        // updateSettings lo luôn việc xoá ảnh cũ khi URL đổi — không xoá lần hai ở đây.
        updateSettings(Map.of(SiteSettingKeys.HERO_IMAGE_URL, publicUrl));
        return publicUrl;
    }
}
