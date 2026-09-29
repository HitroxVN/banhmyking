package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.report.TopProductResponse;
import com.banhmyking.banhmyking.enums.ReportType;

import java.time.LocalDate;
import java.util.List;

/** Báo cáo bán hàng dành cho Admin — chỉ đọc dữ liệu đơn hàng sẵn có. */
public interface AdminReportService {

    /** Món bán chạy nhất trong khoảng ngày (bao gồm cả ngày kết thúc). */
    List<TopProductResponse> getTopProducts(LocalDate fromDate, LocalDate toDate, int limit);

    /** Nội dung file CSV theo loại báo cáo, kèm BOM để Excel đọc đúng tiếng Việt. */
    byte[] exportCsv(ReportType type, LocalDate fromDate, LocalDate toDate, int limit);
}
