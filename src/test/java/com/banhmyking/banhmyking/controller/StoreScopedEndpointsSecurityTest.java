package com.banhmyking.banhmyking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Kiểm tra phân quyền đầu-cuối (filter chain Spring Security + JWT thật + StoreAccessGuard) cho
 * {@code /manager/**} và {@code /store-inventory/**} — spec §4/§9: ngoài phạm vi cơ sở trả 404,
 * sai vai trò trả 403.
 *
 * <p>Chạy trên DB dev nhưng tự tạo cơ sở + tài khoản trong transaction của test; MockMvc chạy cùng
 * luồng nên dùng chung transaction đó và mọi thứ được rollback sau mỗi test.
 */
@SpringBootTest
@Transactional
class StoreScopedEndpointsSecurityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private StoreRepository storeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private Store storeA;
    private Store storeB;
    private User staffA;
    private User managerA;
    private User shipperA;
    private User customer;
    private User admin;

    @BeforeEach
    void setUp() {
        Filter securityChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();

        String tag = Long.toString(System.nanoTime(), 36).toUpperCase();
        storeA = storeRepository.save(newStore("SA" + tag, "Sec A " + tag));
        storeB = storeRepository.save(newStore("SB" + tag, "Sec B " + tag));
        staffA = userRepository.save(newUser(tag, "staff", RoleName.STAFF, storeA));
        managerA = userRepository.save(newUser(tag, "manager", RoleName.MANAGER, storeA));
        shipperA = userRepository.save(newUser(tag, "shipper", RoleName.SHIPPER, storeA));
        customer = userRepository.save(newUser(tag, "customer", RoleName.CUSTOMER, null));
        admin = userRepository.save(newUser(tag, "admin", RoleName.ADMIN, null));
        storeRepository.flush();
    }

    // ------------------------------------------------------------------ /manager/**

    @Test
    void staffGets403OnManagerEndpoints() throws Exception {
        mockMvc.perform(as(staffA, get("/api/v1/manager/dashboard/metrics")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
        mockMvc.perform(as(staffA, accepting(storeA.getId(), false)))
                .andExpect(status().isForbidden());
        assertThat(storeRepository.findById(storeA.getId()).orElseThrow().isAcceptingOrders()).isTrue();
    }

    @Test
    void customerAndShipperGet403OnManagerEndpoints() throws Exception {
        mockMvc.perform(as(customer, get("/api/v1/manager/staff"))).andExpect(status().isForbidden());
        mockMvc.perform(as(shipperA, get("/api/v1/manager/reports/top-products"))).andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401OnManagerEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/manager/dashboard/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void managerOfAnotherStoreGets404OnAccepting() throws Exception {
        mockMvc.perform(as(managerA, accepting(storeB.getId(), false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        assertThat(storeRepository.findById(storeB.getId()).orElseThrow().isAcceptingOrders()).isTrue();
    }

    @Test
    void managerCanToggleAcceptingOnOwnStore() throws Exception {
        mockMvc.perform(as(managerA, accepting(storeA.getId(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(storeA.getId()))
                .andExpect(jsonPath("$.data.acceptingOrders").value(false));
        assertThat(storeRepository.findById(storeA.getId()).orElseThrow().isAcceptingOrders()).isFalse();
    }

    @Test
    void managerReadsOwnStoreDashboardAndStaff() throws Exception {
        mockMvc.perform(as(managerA, get("/api/v1/manager/dashboard/metrics"))).andExpect(status().isOk());
        mockMvc.perform(as(managerA, get("/api/v1/manager/staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3)); // staff + manager + shipper của cơ sở A
    }

    // ------------------------------------------------------------- /store-inventory/**

    @Test
    void staffReadsOwnStoreInventory() throws Exception {
        mockMvc.perform(as(staffA, get(inventory(storeA.getId())))).andExpect(status().isOk());
    }

    @Test
    void staffOfAnotherStoreGets404OnInventory() throws Exception {
        mockMvc.perform(as(staffA, get(inventory(storeB.getId()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void shipperAndCustomerGet403OnInventory() throws Exception {
        // URL matcher chỉ cho STAFF/MANAGER/ADMIN — chặn trước khi tới StoreAccessGuard.
        mockMvc.perform(as(shipperA, get(inventory(storeA.getId())))).andExpect(status().isForbidden());
        mockMvc.perform(as(customer, get(inventory(storeA.getId())))).andExpect(status().isForbidden());
    }

    @Test
    void adminGets404ForUnknownStoreInventory() throws Exception {
        long missingId = storeB.getId() + 1_000_000L;
        mockMvc.perform(as(admin, get(inventory(missingId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void adminGets404ForSoftDeletedStoreInventoryWrite() throws Exception {
        storeB.setDeleted(true);
        storeRepository.saveAndFlush(storeB);
        mockMvc.perform(as(admin, patch(inventory(storeB.getId()) + "/1/availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"available\":false}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void adminReadsAnyActiveStoreInventory() throws Exception {
        mockMvc.perform(as(admin, get(inventory(storeB.getId())))).andExpect(status().isOk());
    }

    // ----------------------------------------------------------------------- helpers

    private MockHttpServletRequestBuilder as(User user, MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(user));
    }

    private static MockHttpServletRequestBuilder accepting(Long storeId, boolean accepting) {
        return patch("/api/v1/manager/stores/" + storeId + "/accepting")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accepting\":" + accepting + "}");
    }

    private static String inventory(Long storeId) {
        return "/api/v1/store-inventory/" + storeId + "/products";
    }

    private static Store newStore(String code, String name) {
        Store store = new Store();
        store.setCode(code);
        store.setName(name);
        store.setAddress("Địa chỉ thử nghiệm");
        return store;
    }

    private static User newUser(String tag, String kind, RoleName role, Store store) {
        User user = new User();
        user.setEmail(kind + "-" + tag.toLowerCase() + "@test.local");
        user.setPassword("not-used");
        user.setFullName("Sec " + kind);
        user.setRole(role);
        user.setStore(store);
        return user;
    }
}
