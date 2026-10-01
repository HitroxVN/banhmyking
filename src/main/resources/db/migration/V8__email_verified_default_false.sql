-- V8: email_verified mặc định FALSE (plan.md §2.2, §5.1 P0)
-- V3 để DEFAULT TRUE → mọi luồng INSERT quên set cột này đều tạo ra tài khoản "đã xác thực".
-- Chỉ đổi DEFAULT cho bản ghi mới; user hiện có giữ nguyên giá trị.
-- Seed (DataInitializer) và ADMIN tạo tài khoản nội bộ đã set TRUE tường minh ở tầng code.
ALTER TABLE users
    MODIFY COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
