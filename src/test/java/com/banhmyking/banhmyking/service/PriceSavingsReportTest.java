package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.dto.report.PriceSavingsResponse;
import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.OrderItem;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import jakarta.servlet.Filter;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tiền ưu đãi từ giá KM và combo (spec combo-sale §6.5) trên DB dev thật: tự tạo cơ sở + đơn trong
 * transaction của test rồi rollback. MockMvc chạy cùng luồng nên dùng chung transaction.
 */
@SpringBootTest
@Transactional
class PriceSavingsReportTest {

    @Autowired private AdminReportService reportService;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private WebApplicationContext context;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private Store storeX;
    private Store storeY;
    private User managerX;
    private User staffX;
    private User admin;

    @BeforeEach
    void setUp() {
        Filter securityChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();

        String tag = Long.toString(System.nanoTime(), 36).toUpperCase();
        storeX = storeRepository.save(store("PX" + tag));
        storeY = storeRepository.save(store("PY" + tag));
        User customer = userRepository.save(user(tag, "customer", RoleName.CUSTOMER, null));
        managerX = userRepository.save(user(tag, "manager", RoleName.MANAGER, storeX));
        staffX = userRepository.save(user(tag, "staff", RoleName.STAFF, storeX));
        admin = userRepository.save(user(tag, "admin", RoleName.ADMIN, null));

        // X: 1 đơn giao có ưu đãi (2 × 5.000) + 1 dòng đơn cũ NULL; 1 đơn giao không ưu đãi; 1 đơn huỷ có ưu đãi
        Order delivered = order(customer, storeX, tag + "1", OrderStatus.DELIVERED);
        delivered.getItems().add(item(delivered, "30000", "25000", 2));
        delivered.getItems().add(item(delivered, null, "20000", 1));
        orderRepository.save(delivered);
        Order noSaving = order(customer, storeX, tag + "2", OrderStatus.DELIVERED);
        noSaving.getItems().add(item(noSaving, "20000", "20000", 3));
        orderRepository.save(noSaving);
        Order cancelled = order(customer, storeX, tag + "3", OrderStatus.CANCELLED);
        cancelled.getItems().add(item(cancelled, "50000", "40000", 1));
        orderRepository.save(cancelled);
        // Y: 2 đơn giao có ưu đãi (15.000 + 3.000)
        Order comboOrder = order(customer, storeY, tag + "4", OrderStatus.DELIVERED);
        comboOrder.getItems().add(item(comboOrder, "70000", "55000", 1));
        orderRepository.save(comboOrder);
        Order saleOrder = order(customer, storeY, tag + "5", OrderStatus.DELIVERED);
        saleOrder.getItems().add(item(saleOrder, "33000", "30000", 1));
        orderRepository.save(saleOrder);
        orderRepository.flush();
    }

    @Test
    void sumsOnlyDeliveredDiscountedLinesPerStore() {
        LocalDate from = LocalDate.now().minusDays(1);
        LocalDate to = LocalDate.now().plusDays(1);

        PriceSavingsResponse x = reportService.getPriceSavings(from, to, storeX.getId());
        assertThat(x.getAmount()).isEqualByComparingTo("10000");
        assertThat(x.getOrderCount()).isEqualTo(1L);

        PriceSavingsResponse y = reportService.getPriceSavings(from, to, storeY.getId());
        assertThat(y.getAmount()).isEqualByComparingTo("18000");
        assertThat(y.getOrderCount()).isEqualTo(2L);

        PriceSavingsResponse chain = reportService.getPriceSavings(from, to, null);
        assertThat(chain.getAmount()).isGreaterThanOrEqualTo(new BigDecimal("28000"));
    }

    @Test
    void emptyRangeReturnsZero() {
        PriceSavingsResponse result = reportService.getPriceSavings(
                LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 2), storeX.getId());
        assertThat(result.getAmount()).isEqualByComparingTo("0");
        assertThat(result.getOrderCount()).isZero();
    }

    @Test
    void managerSeesOwnStoreAdminFiltersStaffForbidden() throws Exception {
        mockMvc.perform(as(managerX, get("/api/v1/manager/reports/price-savings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(1));
        mockMvc.perform(as(admin, get("/api/v1/admin/reports/price-savings").param("storeId", storeY.getId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderCount").value(2));
        mockMvc.perform(as(staffX, get("/api/v1/manager/reports/price-savings")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(managerX, get("/api/v1/admin/reports/price-savings")))
                .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user));
    }

    private static Store store(String code) {
        Store store = new Store();
        store.setCode(code);
        store.setName("Cơ sở " + code);
        store.setAddress("Địa chỉ thử nghiệm");
        return store;
    }

    private static User user(String tag, String kind, RoleName role, Store store) {
        User user = new User();
        user.setEmail("ps-" + kind + "-" + tag.toLowerCase() + "@test.local");
        user.setPassword("not-used");
        user.setFullName("PS " + kind);
        user.setRole(role);
        user.setStore(store);
        return user;
    }

    private static Order order(User customer, Store store, String suffix, OrderStatus status) {
        Order order = new Order();
        order.setOrderCode("PS-" + suffix);
        order.setUser(customer);
        order.setStore(store);
        order.setStatus(status);
        order.setReceiverName("Khách thử");
        order.setReceiverPhone("0900000000");
        order.setShippingAddress("1 Đường Thử");
        order.setSubtotal(new BigDecimal("100000"));
        order.setTotal(new BigDecimal("100000"));
        return order;
    }

    private static OrderItem item(Order order, String original, String unit, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrder(order);
        item.setProductName("Món thử");
        item.setUnitPrice(new BigDecimal(unit));
        item.setOriginalUnitPrice(original == null ? null : new BigDecimal(original));
        item.setQuantity(quantity);
        item.setLineTotal(new BigDecimal(unit).multiply(BigDecimal.valueOf(quantity)));
        return item;
    }
}
