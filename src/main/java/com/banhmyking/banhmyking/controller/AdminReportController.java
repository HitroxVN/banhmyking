package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.report.PriceSavingsResponse;
import com.banhmyking.banhmyking.dto.report.StoreRevenueResponse;
import com.banhmyking.banhmyking.dto.report.TopProductResponse;
import com.banhmyking.banhmyking.enums.ReportType;
import com.banhmyking.banhmyking.service.AdminReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@Tag(name = "Admin Reports", description = "Báo cáo bán hàng và xuất dữ liệu CSV dành cho Admin")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping("/top-products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Bảng món bán chạy",
            description = "Gộp theo món trong khoảng ngày, bỏ đơn đã huỷ / giao thất bại. Không truyền ngày = 30 ngày gần nhất.")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts(
            @Parameter(description = "Ngày bắt đầu (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Ngày kết thúc, tính cả ngày này (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long storeId) {
        List<TopProductResponse> response = adminReportService.getTopProducts(fromDate, toDate, limit, storeId);
        return ResponseEntity.ok(ApiResponse.ok("Lấy bảng món bán chạy thành công", response));
    }

    @GetMapping("/revenue-by-store")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Doanh thu theo cơ sở")
    public ResponseEntity<ApiResponse<List<StoreRevenueResponse>>> revenueByStore(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy doanh thu theo cơ sở thành công",
                adminReportService.getRevenueByStore(fromDate, toDate)));
    }

    @GetMapping("/price-savings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tiền ưu đãi từ giá KM và combo",
            description = "Σ (giá gốc − giá bán) × số lượng trên đơn đã giao. Không truyền ngày = 30 ngày gần nhất.")
    public ResponseEntity<ApiResponse<PriceSavingsResponse>> priceSavings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy tiền ưu đãi thành công",
                adminReportService.getPriceSavings(fromDate, toDate, storeId)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xuất báo cáo CSV",
            description = "Trả thẳng file CSV (UTF-8 kèm BOM) theo loại báo cáo và khoảng ngày đã chọn.")
    public ResponseEntity<byte[]> export(
            @RequestParam ReportType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Long storeId) {
        byte[] csv = adminReportService.exportCsv(type, fromDate, toDate, limit, storeId);

        String today = LocalDate.now().toString();
        String fileName = type.fileName(fromDate != null ? fromDate.toString() : today,
                toDate != null ? toDate.toString() : today);

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(csv);
    }
}
