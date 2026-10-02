package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.banhmyking.banhmyking.dto.dashboard.DashboardMetricsResponse;
import com.banhmyking.banhmyking.dto.report.StoreRevenueResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminReportServiceStoreTest {

    @Autowired private AdminDashboardService dashboardService;
    @Autowired private AdminReportService reportService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;

    /**
     * Cộng trên MỌI cơ sở (kể cả đã xoá mềm): đơn của cơ sở đã xoá vẫn nằm trong tổng toàn chuỗi,
     * nên lọc is_deleted sẽ làm bất biến sai tuỳ dữ liệu DB dev.
     */
    @Test
    void storeFilteredTotalsAddUpToChainTotal() {
        List<Long> storeIds = jdbcTemplate.queryForList("SELECT id FROM stores", Long.class);
        DashboardMetricsResponse chain = dashboardService.getDashboardMetrics(null);
        long sum = storeIds.stream().mapToLong(id -> dashboardService.getDashboardMetrics(id).getTotalOrders()).sum();
        assertThat(sum).isEqualTo(chain.getTotalOrders());
    }

    /**
     * Tự tạo dữ liệu (2 cơ sở + đơn) trong transaction của test rồi rollback — không phụ thuộc
     * nội dung DB dev trên từng máy.
     */
    @Test
    void revenueByStoreAggregatesOwnSeededOrdersPerStore() {
        String tag = Long.toString(System.nanoTime(), 36).toUpperCase();
        Store storeX = storeRepository.save(newStore("RX" + tag, "Report X " + tag));
        Store storeY = storeRepository.save(newStore("RY" + tag, "Report Y " + tag));
        Store storeZ = storeRepository.save(newStore("RZ" + tag, "Report Z " + tag));
        User customer = new User();
        customer.setEmail("report-" + tag.toLowerCase() + "@test.local");
        customer.setPassword("not-used");
        customer.setFullName("Report Test");
        customer.setRole(RoleName.CUSTOMER);
        customer = userRepository.save(customer);

        orderRepository.save(newOrder(customer, storeX, tag + "1", OrderStatus.DELIVERED, "50000"));
        orderRepository.save(newOrder(customer, storeX, tag + "2", OrderStatus.CONFIRMED, "30000"));
        orderRepository.save(newOrder(customer, storeX, tag + "3", OrderStatus.CANCELLED, "99000"));
        orderRepository.save(newOrder(customer, storeY, tag + "4", OrderStatus.PENDING, "20000"));
        orderRepository.save(newOrder(customer, storeZ, tag + "5", OrderStatus.FAILED, "45000"));
        orderRepository.flush();

        List<StoreRevenueResponse> rows = reportService.getRevenueByStore(
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(1));

        StoreRevenueResponse x = rowOf(rows, storeX.getId());
        assertThat(x.getStoreName()).isEqualTo(storeX.getName());
        assertThat(x.getOrderCount()).isEqualTo(2L);
        assertThat(x.getRevenue()).isEqualByComparingTo("80000");
        StoreRevenueResponse y = rowOf(rows, storeY.getId());
        assertThat(y.getOrderCount()).isEqualTo(1L);
        assertThat(y.getRevenue()).isEqualByComparingTo("20000");
        // Cơ sở chỉ có đơn huỷ/thất bại không có doanh thu → không có dòng.
        assertThat(rows).noneMatch(r -> r.getStoreId().equals(storeZ.getId()));
        assertThat(rows).allSatisfy(r -> assertThat(r.getStoreName()).isNotBlank());
    }

    private static StoreRevenueResponse rowOf(List<StoreRevenueResponse> rows, Long storeId) {
        return rows.stream().filter(r -> r.getStoreId().equals(storeId)).findFirst()
                .orElseThrow(() -> new AssertionError("Thiếu dòng doanh thu cho cơ sở " + storeId));
    }

    private static Store newStore(String code, String name) {
        Store store = new Store();
        store.setCode(code);
        store.setName(name);
        store.setAddress("Địa chỉ thử nghiệm");
        return store;
    }

    private static Order newOrder(User customer, Store store, String suffix, OrderStatus status, String total) {
        Order order = new Order();
        order.setOrderCode("RPT-" + suffix);
        order.setUser(customer);
        order.setStore(store);
        order.setStatus(status);
        order.setReceiverName("Khách thử");
        order.setReceiverPhone("0900000000");
        order.setShippingAddress("1 Đường Thử");
        order.setSubtotal(new BigDecimal(total));
        order.setTotal(new BigDecimal(total));
        return order;
    }
}
