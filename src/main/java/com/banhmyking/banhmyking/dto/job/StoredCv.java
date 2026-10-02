package com.banhmyking.banhmyking.dto.job;

/** Kết quả lưu CV: khoá file ngẫu nhiên + tên gốc (để tải về) + loại suy ra từ chữ ký file. */
public record StoredCv(String fileKey, String originalName, String contentType) {
}
