-- V4: quên mật khẩu — token reset riêng, không dùng chung với token xác thực email
ALTER TABLE users
    ADD COLUMN reset_token_hash        VARCHAR(64) NULL AFTER verification_token_expires_at,
    ADD COLUMN reset_token_expires_at  DATETIME    NULL AFTER reset_token_hash;
