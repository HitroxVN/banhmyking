package com.banhmyking.banhmyking.controller.address;

import com.banhmyking.banhmyking.dto.address.AddressRequest;
import com.banhmyking.banhmyking.dto.address.AddressResponse;
import com.banhmyking.banhmyking.exception.GlobalExceptionHandler;
import com.banhmyking.banhmyking.service.AddressService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AddressControllerTest {

    @Mock AddressService addressService;
    @InjectMocks AddressController addressController;

    MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();

    private static final AddressResponse ADDRESS = AddressResponse.builder()
            .id(10L).userId(5L)
            .receiverName("Nguyễn Văn A").receiverPhone("0901234567")
            .fullAddress("45 Nguyễn Huệ, Q1, TP.HCM")
            .defaultAddress(true)
            .build();

    /** Principal giả — username là userId (JWT subject), user 5. */
    private static final UserDetails PRINCIPAL =
            org.springframework.security.core.userdetails.User
                    .withUsername("5")
                    .password("x")
                    .authorities("ROLE_CUSTOMER")
                    .build();

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(addressController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        // @AuthenticationPrincipal resolve từ SecurityContextHolder, không phải request principal
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(PRINCIPAL, null, PRINCIPAL.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAddresses_returnsOwnList() throws Exception {
        when(addressService.getAddresses(5L)).thenReturn(List.of(ADDRESS));

        mockMvc.perform(get("/api/v1/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].defaultAddress").value(true));
    }

    @Test
    void getAddresses_ignoresXUserIdHeader() throws Exception {
        // IDOR regression: header X-User-Id phải bị bỏ qua hoàn toàn — danh tính chỉ từ JWT
        when(addressService.getAddresses(5L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/addresses")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());

        verify(addressService).getAddresses(5L);
    }

    @Test
    void getAddress_returnsDetail() throws Exception {
        when(addressService.getAddress(5L, 10L)).thenReturn(ADDRESS);

        mockMvc.perform(get("/api/v1/addresses/10")
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));

        verify(addressService).getAddress(5L, 10L);
    }

    @Test
    void createAddress_returns201() throws Exception {
        AddressRequest request = AddressRequest.builder()
                .receiverName("Nguyễn Văn A").receiverPhone("0901234567")
                .fullAddress("45 Nguyễn Huệ, Q1, TP.HCM")
                .defaultAddress(true)
                .build();
        when(addressService.createAddress(any(), any())).thenReturn(ADDRESS);

        mockMvc.perform(post("/api/v1/addresses")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(10));

        verify(addressService).createAddress(eq(5L), any(AddressRequest.class));
    }

    @Test
    void createAddress_invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAddress_returnsUpdated() throws Exception {
        AddressRequest request = AddressRequest.builder()
                .receiverName("Nguyễn Văn B").receiverPhone("0909876543")
                .fullAddress("10 Lê Lợi, Q1, TP.HCM")
                .defaultAddress(false)
                .build();
        when(addressService.updateAddress(eq(5L), eq(10L), any())).thenReturn(ADDRESS);

        mockMvc.perform(put("/api/v1/addresses/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10));
    }

    @Test
    void deleteAddress_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/addresses/10"))
                .andExpect(status().isOk());

        verify(addressService).deleteAddress(5L, 10L);
    }

    @Test
    void setDefaultAddress_returnsUpdated() throws Exception {
        when(addressService.setDefaultAddress(5L, 10L)).thenReturn(ADDRESS);

        mockMvc.perform(patch("/api/v1/addresses/10/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultAddress").value(true));
    }
}
