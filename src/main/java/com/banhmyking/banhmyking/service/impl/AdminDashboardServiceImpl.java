package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.dashboard.DailyRevenueResponse;
import com.banhmyking.banhmyking.dto.dashboard.DashboardMetricsResponse;
import com.banhmyking.banhmyking.dto.dashboard.OrderStatusStatResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Override
    @Transactional(readOnly = true)
    public DashboardMetricsResponse getDashboardMetrics(Long storeId) {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        BigDecimal totalRevenue = orderRepository.sumRevenue(storeId, null);
        BigDecimal todayRevenue = orderRepository.sumRevenue(storeId, startOfToday);

        long totalOrders = orderRepository.countOrders(storeId, null);
        long todayOrders = orderRepository.countOrders(storeId, startOfToday);

        long pendingOrders = orderRepository.countByStatuses(storeId, List.of(OrderStatus.PENDING));
        long processingOrders = orderRepository.countByStatuses(storeId, List.of(
                OrderStatus.CONFIRMED,
                OrderStatus.PREPARING,
                OrderStatus.READY_FOR_PICKUP,
                OrderStatus.DELIVERING
        ));
        long deliveredOrders = orderRepository.countByStatuses(storeId, List.of(OrderStatus.DELIVERED));
        long cancelledOrders = orderRepository.countByStatuses(storeId, List.of(
                OrderStatus.CANCELLED,
                OrderStatus.FAILED
        ));

        double successRate = totalOrders > 0
                ? Math.round(((double) deliveredOrders / totalOrders) * 1000.0) / 10.0
                : 0.0;

        long totalUsers;
        long customerCount;
        long staffCount;
        long shipperCount;
        if (storeId == null) {
            totalUsers = userRepository.countByDeletedFalse();
            customerCount = userRepository.countByRoleAndDeletedFalse(RoleName.CUSTOMER);
            staffCount = userRepository.countByRoleAndDeletedFalse(RoleName.STAFF);
            shipperCount = userRepository.countByRoleAndDeletedFalse(RoleName.SHIPPER);
        } else {
            // Khách hàng không thuộc cơ sở nào — chỉ đếm nhân sự của cơ sở.
            List<User> storeUsers = userRepository.findByStoreIdAndDeletedFalseOrderByRoleAscFullNameAsc(storeId);
            staffCount = storeUsers.stream().filter(u -> u.getRole() == RoleName.STAFF).count();
            shipperCount = storeUsers.stream().filter(u -> u.getRole() == RoleName.SHIPPER).count();
            long managerCount = storeUsers.stream().filter(u -> u.getRole() == RoleName.MANAGER).count();
            customerCount = 0;
            totalUsers = staffCount + shipperCount + managerCount;
        }

        return DashboardMetricsResponse.builder()
                .totalRevenue(totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .todayRevenue(todayRevenue != null ? todayRevenue : BigDecimal.ZERO)
                .totalOrders(totalOrders)
                .todayOrders(todayOrders)
                .pendingOrders(pendingOrders)
                .processingOrders(processingOrders)
                .deliveredOrders(deliveredOrders)
                .cancelledOrders(cancelledOrders)
                .successRate(successRate)
                .totalUsers(totalUsers)
                .customerCount(customerCount)
                .staffCount(staffCount)
                .shipperCount(shipperCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<DailyRevenueResponse> getDailyRevenueChart(int days, Long storeId) {
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDate startDate = LocalDate.now().minusDays(safeDays - 1);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        List<Order> orders = orderRepository.findForChart(storeId, startDateTime);

        Map<LocalDate, BigDecimal> revenueMap = new HashMap<>();
        Map<LocalDate, Long> countMap = new HashMap<>();

        for (Order order : orders) {
            LocalDate orderDate = order.getCreatedAt().toLocalDate();
            if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.FAILED) {
                revenueMap.merge(orderDate, order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO, BigDecimal::add);
            }
            countMap.merge(orderDate, 1L, Long::sum);
        }

        List<DailyRevenueResponse> result = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            LocalDate d = startDate.plusDays(i);
            String dateStr = d.format(DATE_FORMATTER);
            BigDecimal rev = revenueMap.getOrDefault(d, BigDecimal.ZERO);
            long cnt = countMap.getOrDefault(d, 0L);

            result.add(DailyRevenueResponse.builder()
                    .date(dateStr)
                    .revenue(rev)
                    .orderCount(cnt)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderStatusStatResponse> getOrderStatusStats(Long storeId) {
        long totalOrders = orderRepository.countOrders(storeId, null);
        List<OrderStatusStatResponse> result = new ArrayList<>();

        for (OrderStatus status : OrderStatus.values()) {
            long count = orderRepository.countByStatuses(storeId, List.of(status));
            double percentage = totalOrders > 0
                    ? Math.round(((double) count / totalOrders) * 1000.0) / 10.0
                    : 0.0;

            result.add(OrderStatusStatResponse.builder()
                    .status(status)
                    .statusLabel(getStatusLabelVi(status))
                    .count(count)
                    .percentage(percentage)
                    .totalAmount(BigDecimal.ZERO)
                    .build());
        }

        return result;
    }

    private String getStatusLabelVi(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case PREPARING -> "Đang làm bánh";
            case READY_FOR_PICKUP -> "Chờ tài xế lấy";
            case DELIVERING -> "Đang giao hàng";
            case DELIVERED -> "Giao thành công";
            case CANCELLED -> "Đã hủy";
            case FAILED -> "Giao thất bại";
        };
    }
}
