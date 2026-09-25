-- V3: xác thực email khi đăng ký
-- DEFAULT TRUE để user cũ + seed us
--
-- er không bị khoá khỏi hệ thống; user mới set FALSE tường minh ở tầng code.
ALTER TABLE users
    ADD COLUMN email_verified                 BOOLEAN      NOT NULL DEFAULT TRUE AFTER is_banned,
    ADD COLUMN verification_token_hash        VARCHAR(64)  NULL AFTER email_verified,
    ADD COLUMN verification_token_expires_at  DATETIME     NULL AFTER verification_token_hash;
