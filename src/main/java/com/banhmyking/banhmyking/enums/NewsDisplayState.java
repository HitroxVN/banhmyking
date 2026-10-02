package com.banhmyking.banhmyking.enums;

/** Trạng thái hiển thị suy ra từ status + published_at so với Clock — dùng cho bộ lọc admin (spec D §3). */
public enum NewsDisplayState {
    DRAFT,
    SCHEDULED,
    PUBLISHED
}
