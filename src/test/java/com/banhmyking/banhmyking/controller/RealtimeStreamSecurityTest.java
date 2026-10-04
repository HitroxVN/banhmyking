package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.JwtTokenProvider;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Luồng realtime đi qua filter chain + JWT thật (spec realtime §4, §7). DB dev, rollback sau mỗi test. */
@SpringBootTest
@Transactional
class RealtimeStreamSecurityTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private User customer;

    @BeforeEach
    void setUp() {
        Filter securityChain = context.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(securityChain).build();

        User user = new User();
        user.setEmail("realtime-" + Long.toString(System.nanoTime(), 36) + "@test.local");
        user.setPassword("not-used");
        user.setFullName("Khách realtime");
        user.setRole(RoleName.CUSTOMER);
        customer = userRepository.save(user);
    }

    @Test
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/realtime/stream")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserGetsEventStreamStartingWithReady() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/realtime/stream")
                        .header("Authorization", "Bearer " + jwtTokenProvider.generateAccessToken(customer))
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
        assertThat(result.getResponse().getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(result.getResponse().getContentAsString()).contains("event:ready").contains("connectionId");
    }
}
