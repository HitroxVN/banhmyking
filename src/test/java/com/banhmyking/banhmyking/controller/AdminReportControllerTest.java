package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.report.TopProductResponse;
import com.banhmyking.banhmyking.enums.ReportType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.AdminReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc nên KHÔNG kiểm tra được phân quyền — SecurityConfig đã gác {@code /api/v1/admin/**}
 * cho ADMIN, còn {@code @PreAuthorize} trên controller chỉ có hiệu lực trong context đầy đủ.
 */
@ExtendWith(MockitoExtension.class)
class AdminReportControllerTest {

    @Mock
    private AdminReportService adminReportService;

    @InjectMocks
    private AdminReportController adminReportController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(adminReportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /top-products trả envelope JSON kèm số liệu gộp theo món")
    void getTopProducts_returnsRows() throws Exception {
        when(adminReportService.getTopProducts(any(), any(), eq(10))).thenReturn(List.of(
                new TopProductResponse(10L, "Bánh mì Đặc Biệt", 12L, BigDecimal.valueOf(420000))));

        mockMvc.perform(get("/api/v1/admin/reports/top-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].productName").value("Bánh mì Đặc Biệt"))
                .andExpect(jsonPath("$.data[0].quantitySold").value(12))
                .andExpect(jsonPath("$.data[0].revenue").value(420000));
    }

    @Test
    @DisplayName("GET /top-products với ngày bắt đầu sau ngày kết thúc trả 400 kèm mã lỗi VALIDATION_ERROR")
    void getTopProducts_whenFromAfterTo_returnsBadRequest() throws Exception {
        when(adminReportService.getTopProducts(any(), any(), eq(10))).thenThrow(
                new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc"));

        mockMvc.perform(get("/api/v1/admin/reports/top-products")
                        .param("fromDate", "2026-02-01")
                        .param("toDate", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("GET /export trả file CSV đính kèm, tên file mang đúng loại báo cáo và khoảng ngày")
    void export_returnsCsvAttachment() throws Exception {
        byte[] csv = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'A'};
        when(adminReportService.exportCsv(eq(ReportType.TOP_PRODUCTS), any(), any(), eq(10))).thenReturn(csv);

        MvcResult result = mockMvc.perform(get("/api/v1/admin/reports/export")
                        .param("type", "TOP_PRODUCTS")
                        .param("fromDate", "2026-01-01")
                        .param("toDate", "2026-01-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"top-mon_2026-01-01_2026-01-31.csv\""))
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/csv");
        assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(csv);
    }

    @Test
    @DisplayName("GET /export với loại báo cáo không tồn tại trả 400")
    void export_whenTypeUnknown_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports/export").param("type", "KHONG_CO_TON"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /export không truyền ngày vẫn xuất được (mặc định 30 ngày gần nhất)")
    void export_whenNoDates_usesDefaults() throws Exception {
        byte[] csv = "Nội dung".getBytes(StandardCharsets.UTF_8);
        when(adminReportService.exportCsv(eq(ReportType.REVENUE_BY_DAY), any(), any(), eq(10))).thenReturn(csv);

        mockMvc.perform(get("/api/v1/admin/reports/export").param("type", "REVENUE_BY_DAY"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("doanh-thu-theo-ngay_")));
    }
}
