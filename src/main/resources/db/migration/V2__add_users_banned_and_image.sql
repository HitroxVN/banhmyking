-- V2: khoá/mở tài khoản (is_banned) + avatar URL (image)
ALTER TABLE users
    ADD COLUMN is_banned BOOLEAN NOT NULL DEFAULT FALSE AFTER role,
    ADD COLUMN image     VARCHAR(255) NULL AFTER phone;
