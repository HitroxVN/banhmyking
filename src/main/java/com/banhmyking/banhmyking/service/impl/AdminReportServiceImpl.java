package com.banhmyking.banhmyking.service.impl;

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
import com.banhmyking.banhmyking.service.AdminReportService;
import com.banhmyking.banhmyking.util.CsvWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class AdminReportServiceImpl implements AdminReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /** Mặc định 30 ngày gần nhất; trần 100 dòng để CSV không phình vô hạn. */
    private static final int DEFAULT_RANGE_DAYS = 30;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    @Override
    @Transactional(readOnly = true)
    public List<TopProductResponse> getTopProducts(LocalDate fromDate, LocalDate toDate, int limit) {
        LocalDate from = resolveFrom(fromDate, toDate);
        LocalDate to = resolveTo(toDate);
        assertValidRange(from, to);

        return fetchTopProducts(from, to, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(ReportType type, LocalDate fromDate, LocalDate toDate, int limit) {
        LocalDate from = resolveFrom(fromDate, toDate);
        LocalDate to = resolveTo(toDate);
        assertValidRange(from, to);

        List<String[]> rows = switch (type) {
            case TOP_PRODUCTS -> topProductRows(from, to, limit);
            case REVENUE_BY_DAY -> revenueByDayRows(from, to);
            case REVENUE_BY_CATEGORY -> revenueByCategoryRows(from, to);
            case REVENUE_BY_SHIPPER -> revenueByShipperRows(from, to);
        };
        return CsvWriter.toBytes(rows);
    }

    private List<TopProductResponse> fetchTopProducts(LocalDate from, LocalDate to, int limit) {
        return orderItemRepository.findTopProducts(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), pageOf(limit));
    }

    // ─── Sinh dòng CSV theo từng loại báo cáo ────────────────────────────────

    private List<String[]> topProductRows(LocalDate from, LocalDate to, int limit) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Món", "Số lượng bán", "Doanh thu (đ)"});
        for (TopProductResponse item : fetchTopProducts(from, to, limit)) {
            rows.add(new String[] {
                    item.getProductName(),
                    String.valueOf(item.getQuantitySold()),
                    plain(item.getRevenue()),
            });
        }
        return rows;
    }

    private List<String[]> revenueByDayRows(LocalDate from, LocalDate to) {
        // Ngày không phát sinh đơn không xuất dòng — CSV là sổ bán hàng, không phải lịch.
        Map<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        Map<LocalDate, Long> ordersByDay = new TreeMap<>();
        for (Order order : orderRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            if (isRevenueOrder(order)) {
                revenueByDay.merge(order.getCreatedAt().toLocalDate(), nullToZero(order.getTotal()), BigDecimal::add);
                ordersByDay.merge(order.getCreatedAt().toLocalDate(), 1L, Long::sum);
            }
        }

        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Ngày", "Số đơn", "Doanh thu (đ)"});
        for (Map.Entry<LocalDate, BigDecimal> entry : revenueByDay.entrySet()) {
            rows.add(new String[] {
                    entry.getKey().toString(),
                    String.valueOf(ordersByDay.getOrDefault(entry.getKey(), 0L)),
                    plain(entry.getValue()),
            });
        }
        return rows;
    }

    private List<String[]> revenueByCategoryRows(LocalDate from, LocalDate to) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Danh mục", "Số lượng bán", "Doanh thu (đ)"});
        for (CategoryRevenueResponse item : orderItemRepository.findRevenueByCategory(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            rows.add(new String[] {
                    item.getCategoryName(),
                    String.valueOf(item.getQuantitySold()),
                    plain(item.getRevenue()),
            });
        }
        return rows;
    }

    private List<String[]> revenueByShipperRows(LocalDate from, LocalDate to) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Tài xế", "Số đơn", "Doanh thu (đ)"});
        for (ShipperRevenueResponse item : orderRepository.findRevenueByShipper(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay())) {
            rows.add(new String[] {
                    item.getShipperName(),
                    String.valueOf(item.getOrderCount()),
                    plain(item.getRevenue()),
            });
        }
        return rows;
    }

    // ─── Chuẩn hoá tham số ───────────────────────────────────────────────────

    /** Không truyền `from` thì lấy `to` lùi 29 ngày; cũng không truyền `to` luôn thì lấy 30 ngày gần nhất. */
    private LocalDate resolveFrom(LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null) {
            return fromDate;
        }
        LocalDate anchor = toDate != null ? toDate : LocalDate.now();
        return anchor.minusDays(DEFAULT_RANGE_DAYS - 1);
    }

    private LocalDate resolveTo(LocalDate toDate) {
        return toDate != null ? toDate : LocalDate.now();
    }

    private void assertValidRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private PageRequest pageOf(int limit) {
        return PageRequest.of(0, clampLimit(limit));
    }

    private boolean isRevenueOrder(Order order) {
        return order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.FAILED;
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    /** Số thô, không dấu phân cách nghìn — để Excel hiểu là số chứ không phải chuỗi. */
    private String plain(BigDecimal value) {
        return nullToZero(value).toPlainString();
    }
}
