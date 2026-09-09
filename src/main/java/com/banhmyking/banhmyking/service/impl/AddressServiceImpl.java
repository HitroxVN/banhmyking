package com.banhmyking.banhmyking.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.banhmyking.banhmyking.dto.address.AddressRequest;
import com.banhmyking.banhmyking.dto.address.AddressResponse;
import com.banhmyking.banhmyking.entity.Address;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.mapper.AddressMapper;
import com.banhmyking.banhmyking.repository.AddressRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.service.AddressService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

	private final AddressRepository addressRepository;
	private final UserRepository userRepository;
	private final AddressMapper addressMapper;

	@Override
	@Transactional(readOnly = true)
	public List<AddressResponse> getAddresses(Long userId) {
		return addressRepository.findByUserId(userId).stream()
				.map(addressMapper::toResponse)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AddressResponse getAddress(Long userId, Long addressId) {
		return addressMapper.toResponse(findAddress(userId, addressId));
	}

	@Override
	@Transactional
	public AddressResponse createAddress(Long userId, AddressRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + userId));
		Address address = addressMapper.toEntity(request);
		address.setUser(user);
		if (address.isDefaultAddress() || addressRepository.findByUserId(userId).isEmpty()) {
			clearDefaultAddress(userId, null);
			address.setDefaultAddress(true);
		}
		return addressMapper.toResponse(addressRepository.save(address));
	}

	@Override
	@Transactional
	public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
		Address address = findAddress(userId, addressId);
		addressMapper.updateEntity(address, request);
		if (address.isDefaultAddress()) {
			clearDefaultAddress(userId, addressId);
		}
		return addressMapper.toResponse(addressRepository.save(address));
	}

	@Override
	@Transactional
	public void deleteAddress(Long userId, Long addressId) {
		Address address = findAddress(userId, addressId);
		boolean wasDefault = address.isDefaultAddress();
		addressRepository.delete(address);
		if (wasDefault) {
			addressRepository.findByUserId(userId).stream().findFirst().ifPresent(next -> {
				next.setDefaultAddress(true);
				addressRepository.save(next);
			});
		}
	}

	@Override
	@Transactional
	public AddressResponse setDefaultAddress(Long userId, Long addressId) {
		Address address = findAddress(userId, addressId);
		clearDefaultAddress(userId, addressId);
		address.setDefaultAddress(true);
		return addressMapper.toResponse(addressRepository.save(address));
	}

	private Address findAddress(Long userId, Long addressId) {
		return addressRepository.findByIdAndUserId(addressId, userId)
				.orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy địa chỉ với ID: " + addressId));
	}

	private void clearDefaultAddress(Long userId, Long excludedAddressId) {
		addressRepository.findByUserId(userId).stream()
				.filter(address -> address.isDefaultAddress()
						&& (excludedAddressId == null || !excludedAddressId.equals(address.getId())))
				.forEach(address -> {
					address.setDefaultAddress(false);
					addressRepository.save(address);
				});
	}
}
