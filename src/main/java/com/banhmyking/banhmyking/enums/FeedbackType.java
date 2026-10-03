package com.banhmyking.banhmyking.enums;

/** Loại phản hồi (spec D §2.5). Nhãn tiếng Việt dùng trong email báo admin. */
public enum FeedbackType {
    SUGGESTION("Góp ý"),
    COMPLAINT("Khiếu nại"),
    PARTNERSHIP("Hợp tác"),
    OTHER("Khác");

    private final String label;

    FeedbackType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
