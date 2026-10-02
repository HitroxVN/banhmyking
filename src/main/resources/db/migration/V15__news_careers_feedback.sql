-- V15: tin tức, tuyển dụng, phản hồi (spec docs/superpowers/specs/2026-10-02-news-careers-feedback-design.md §2)
-- Chỉ tạo bảng mới nên dữ liệu cũ giữ nguyên hợp lệ. Không dùng CHECK: ràng buộc nghiệp vụ
-- kiểm ở service để giữ thông báo tiếng Việt. Slug UNIQUE phủ cả dòng đã xoá mềm.

CREATE TABLE news_posts (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(220) NOT NULL,
    cover_image_url VARCHAR(500) NULL,
    summary         VARCHAR(500) NULL,
    content         MEDIUMTEXT   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    published_at    DATETIME     NULL,
    pinned          BOOLEAN      NOT NULL DEFAULT FALSE,
    author_id       BIGINT       NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NULL,
    CONSTRAINT uk_news_posts_slug UNIQUE (slug),
    CONSTRAINT fk_news_posts_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_news_posts_visible (status, published_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE job_postings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    slug            VARCHAR(220) NOT NULL,
    employment_type VARCHAR(20)  NOT NULL,
    salary_text     VARCHAR(100) NULL,
    headcount       INT          NULL,
    deadline        DATE         NULL,
    description     MEDIUMTEXT   NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NULL,
    CONSTRAINT uk_job_postings_slug UNIQUE (slug),
    INDEX idx_job_postings_open (status, deadline)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Không có dòng nào cho một tin = tuyển toàn chuỗi (mọi cơ sở đang hoạt động).
CREATE TABLE job_posting_stores (
    job_posting_id BIGINT NOT NULL,
    store_id       BIGINT NOT NULL,
    PRIMARY KEY (job_posting_id, store_id),
    CONSTRAINT fk_jps_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id) ON DELETE CASCADE,
    CONSTRAINT fk_jps_store FOREIGN KEY (store_id) REFERENCES stores (id),
    INDEX idx_jps_store (store_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE job_applications (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_posting_id   BIGINT        NOT NULL,
    store_id         BIGINT        NOT NULL,
    full_name        VARCHAR(100)  NOT NULL,
    phone            VARCHAR(20)   NOT NULL,
    email            VARCHAR(150)  NULL,
    message          VARCHAR(2000) NULL,
    cv_file_key      VARCHAR(100)  NULL,
    cv_original_name VARCHAR(255)  NULL,
    cv_content_type  VARCHAR(100)  NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    internal_note    VARCHAR(2000) NULL,
    handled_by       BIGINT        NULL,
    handled_at       DATETIME      NULL,
    client_ip        VARCHAR(45)   NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NULL,
    CONSTRAINT fk_job_applications_job FOREIGN KEY (job_posting_id) REFERENCES job_postings (id),
    CONSTRAINT fk_job_applications_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_job_applications_handler FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_job_applications_store_status (store_id, status),
    INDEX idx_job_applications_dup (job_posting_id, phone, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE feedbacks (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type            VARCHAR(20)   NOT NULL,
    store_id        BIGINT        NULL,
    order_id        BIGINT        NULL,
    user_id         BIGINT        NULL,
    full_name       VARCHAR(100)  NOT NULL,
    phone           VARCHAR(20)   NULL,
    email           VARCHAR(150)  NULL,
    subject         VARCHAR(200)  NOT NULL,
    content         VARCHAR(5000) NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    resolution_note VARCHAR(2000) NULL,
    handled_by      BIGINT        NULL,
    handled_at      DATETIME      NULL,
    client_ip       VARCHAR(45)   NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NULL,
    CONSTRAINT fk_feedbacks_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_feedbacks_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_feedbacks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_feedbacks_handler FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_feedbacks_store_status (store_id, status),
    INDEX idx_feedbacks_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
