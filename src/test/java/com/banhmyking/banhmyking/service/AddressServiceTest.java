package com.banhmyking.banhmyking.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.banhmyking.banhmyking.dto.address.AddressRequest;
import com.banhmyking.banhmyking.dto.address.AddressResponse;
import com.banhmyking.banhmyking.entity.Address;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.mapper.AddressMapper;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.impl.AddressServiceImpl;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AddressMapper addressMapper;

    @InjectMocks
    private AddressServiceImpl addressService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("customer@banhmyking.vn");
    }

    @Test
    void createFirstAddressMakesItDefault() {
        AddressRequest request = request(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(addressRepository.findByUserId(1L)).thenReturn(List.of());
        when(addressMapper.toEntity(request)).thenAnswer(invocation -> address(10L, false));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> {
            Address address = invocation.getArgument(0);
            address.setId(10L);
            return address;
        });
        when(addressMapper.toResponse(any(Address.class))).thenAnswer(invocation -> {
            Address address = invocation.getArgument(0);
            return AddressResponse.builder()
                    .id(address.getId())
                    .userId(address.getUser().getId())
                    .defaultAddress(address.isDefaultAddress())
                    .build();
        });

        var response = addressService.createAddress(1L, request);

        assertThat(response.isDefaultAddress()).isTrue();
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    void settingDefaultClearsOtherDefaultAddress() {
        Address currentDefault = address(10L, true);
        Address selected = address(11L, false);
        when(addressRepository.findByIdAndUserId(11L, 1L)).thenReturn(Optional.of(selected));
        when(addressRepository.findByUserId(1L)).thenReturn(List.of(currentDefault, selected));
        when(addressRepository.save(any(Address.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(addressMapper.toResponse(any(Address.class))).thenReturn(AddressResponse.builder()
            .id(11L)
            .userId(1L)
            .defaultAddress(true)
            .build());

        var response = addressService.setDefaultAddress(1L, 11L);

        assertThat(currentDefault.isDefaultAddress()).isFalse();
        assertThat(response.isDefaultAddress()).isTrue();
        verify(addressRepository).save(currentDefault);
    }

    @Test
    void addressFromAnotherUserIsNotAccessible() {
        when(addressRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.getAddress(2L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(addressRepository, never()).delete(any(Address.class));
    }

    private AddressRequest request(boolean defaultAddress) {
        return AddressRequest.builder()
                .receiverName("Nguyen Van A")
                .receiverPhone("0901234567")
                .fullAddress("123 Le Loi, Quan 1")
                .defaultAddress(defaultAddress)
                .build();
    }

    private Address address(Long id, boolean defaultAddress) {
        Address address = new Address();
        address.setId(id);
        address.setUser(user);
        address.setReceiverName("Nguyen Van A");
        address.setReceiverPhone("0901234567");
        address.setFullAddress("123 Le Loi, Quan 1");
        address.setDefaultAddress(defaultAddress);
        return address;
    }
}
