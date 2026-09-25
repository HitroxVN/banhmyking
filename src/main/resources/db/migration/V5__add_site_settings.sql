-- V5: nội dung website do admin sửa — lưu key/value, thêm giá trị mới không cần migration
CREATE TABLE site_settings (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key   VARCHAR(100)  NOT NULL,
    setting_value TEXT          NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6),
    CONSTRAINT uk_site_settings_key UNIQUE (setting_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
