package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.report.CategoryRevenueResponse;
import com.banhmyking.banhmyking.dto.report.ShipperRevenueResponse;
import com.banhmyking.banhmyking.dto.report.TopProductResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.ReportType;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.OrderItemRepository;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.service.impl.AdminReportServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private AdminReportServiceImpl reportService;

    /** Bỏ 3 byte BOM để so phần nội dung CSV. */
    private String body(byte[] bytes) {
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("limit vượt trần bị kẹp về 100, limit <= 0 dùng mặc định 10")
    void getTopProducts_clampsLimit() {
        when(orderItemRepository.findTopProducts(any(), any(), any(), any())).thenReturn(List.of());

        reportService.getTopProducts(null, null, 500, null);
        reportService.getTopProducts(null, null, 0, null);

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(orderItemRepository, org.mockito.Mockito.times(2))
                .findTopProducts(any(), any(), any(), pageCaptor.capture());

        assertThat(pageCaptor.getAllValues()).extracting(Pageable::getPageSize).containsExactly(100, 10);
    }

    @Test
    @DisplayName("Không truyền ngày: lấy đúng 30 ngày gần nhất, ngày kết thúc tính đến hết hôm nay")
    void getTopProducts_whenNoDates_defaultsToLast30Days() {
        when(orderItemRepository.findTopProducts(any(), any(), any(), any())).thenReturn(List.of());

        reportService.getTopProducts(null, null, 10, null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderItemRepository).findTopProducts(fromCaptor.capture(), toCaptor.capture(), any(), any());

        LocalDate today = LocalDate.now();
        assertThat(fromCaptor.getValue()).isEqualTo(today.minusDays(29).atStartOfDay());
        assertThat(toCaptor.getValue()).isEqualTo(today.plusDays(1).atStartOfDay());
    }

    @Test
    @DisplayName("Ngày kết thúc được tính trọn ngày — cận trên là 00:00 ngày kế tiếp")
    void getTopProducts_endDateIsInclusive() {
        when(orderItemRepository.findTopProducts(any(), any(), any(), any())).thenReturn(List.of());

        reportService.getTopProducts(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 10, null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderItemRepository).findTopProducts(fromCaptor.capture(), toCaptor.capture(), any(), any());

        assertThat(fromCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(toCaptor.getValue()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
    }

    @Test
    @DisplayName("Ngày bắt đầu sau ngày kết thúc bị chặn với VALIDATION_ERROR")
    void getTopProducts_whenFromAfterTo_throwsValidationError() {
        assertThatThrownBy(() -> reportService.getTopProducts(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1), 10, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("CSV món bán chạy có tiêu đề, tên món và số liệu thô không dấu phân cách")
    void exportCsv_topProducts_writesHeaderAndRows() {
        when(orderItemRepository.findTopProducts(any(), any(), any(), any())).thenReturn(List.of(
                new TopProductResponse(10L, "Bánh mì Đặc Biệt", 12L, new BigDecimal("420000.00"))));

        byte[] csv = reportService.exportCsv(ReportType.TOP_PRODUCTS,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 10, null);

        assertThat(body(csv)).isEqualTo(
                "Món,Số lượng bán,Doanh thu (đ)\r\n"
                        + "Bánh mì Đặc Biệt,12,420000.00\r\n");
    }

    @Test
    @DisplayName("Doanh thu theo ngày bỏ đơn CANCELLED/FAILED và chỉ ra ngày có phát sinh đơn")
    void exportCsv_revenueByDay_skipsCancelledOrders() {
        Order delivered = order(OrderStatus.DELIVERED, LocalDate.of(2026, 1, 5), new BigDecimal("95000.00"));
        Order cancelled = order(OrderStatus.CANCELLED, LocalDate.of(2026, 1, 6), new BigDecimal("50000.00"));
        Order failed = order(OrderStatus.FAILED, LocalDate.of(2026, 1, 6), new BigDecimal("30000.00"));
        when(orderRepository.findInRange(any(), any(), any()))
                .thenReturn(List.of(delivered, cancelled, failed));

        byte[] csv = reportService.exportCsv(ReportType.REVENUE_BY_DAY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 10, null);

        assertThat(body(csv)).isEqualTo(
                "Ngày,Số đơn,Doanh thu (đ)\r\n"
                        + "2026-01-05,1,95000.00\r\n");
    }

    @Test
    @DisplayName("CSV doanh thu theo danh mục và theo tài xế có tiêu đề đúng")
    void exportCsv_categoryAndShipper_haveHeaders() {
        when(orderItemRepository.findRevenueByCategory(any(), any(), any())).thenReturn(List.of(
                new CategoryRevenueResponse(3L, "Bánh mì", 20L, new BigDecimal("700000.00"))));
        when(orderRepository.findRevenueByShipper(any(), any(), any())).thenReturn(List.of(
                new ShipperRevenueResponse(4L, "Tài xế Hoàng", 7L, new BigDecimal("665000.00"))));

        byte[] byCategory = reportService.exportCsv(ReportType.REVENUE_BY_CATEGORY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 10, null);
        byte[] byShipper = reportService.exportCsv(ReportType.REVENUE_BY_SHIPPER,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 10, null);

        assertThat(body(byCategory)).isEqualTo(
                "Danh mục,Số lượng bán,Doanh thu (đ)\r\n"
                        + "Bánh mì,20,700000.00\r\n");
        assertThat(body(byShipper)).isEqualTo(
                "Tài xế,Số đơn,Doanh thu (đ)\r\n"
                        + "Tài xế Hoàng,7,665000.00\r\n");
    }

    private Order order(OrderStatus status, LocalDate createdDate, BigDecimal total) {
        Order order = new Order();
        order.setStatus(status);
        order.setCreatedAt(createdDate.atTime(10, 0));
        order.setTotal(total);
        return order;
    }
}
