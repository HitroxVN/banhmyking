package com.banhmyking.banhmyking.service;

import java.util.List;

import com.banhmyking.banhmyking.dto.address.AddressRequest;
import com.banhmyking.banhmyking.dto.address.AddressResponse;

public interface AddressService {

	List<AddressResponse> getAddresses(Long userId);

	AddressResponse getAddress(Long userId, Long addressId);

	AddressResponse createAddress(Long userId, AddressRequest request);

	AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request);

	void deleteAddress(Long userId, Long addressId);

	AddressResponse setDefaultAddress(Long userId, Long addressId);
}
