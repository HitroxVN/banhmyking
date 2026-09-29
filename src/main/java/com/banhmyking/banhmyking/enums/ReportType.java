package com.banhmyking.banhmyking.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Loại báo cáo xuất được ở màn Quản trị → Báo cáo. */
@Getter
@RequiredArgsConstructor
public enum ReportType {

    TOP_PRODUCTS("top-mon"),
    REVENUE_BY_DAY("doanh-thu-theo-ngay"),
    REVENUE_BY_CATEGORY("doanh-thu-theo-danh-muc"),
    REVENUE_BY_SHIPPER("doanh-thu-theo-tai-xe");

    /** Phần tên file, đã bỏ dấu để trình duyệt/Excel không gặp ký tự lạ. */
    private final String slug;

    public String fileName(String fromDate, String toDate) {
        return slug + "_" + fromDate + "_" + toDate + ".csv";
    }
}
