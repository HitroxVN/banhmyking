package com.banhmyking.banhmyking.mapper;

import org.springframework.stereotype.Component;

import com.banhmyking.banhmyking.dto.address.AddressRequest;
import com.banhmyking.banhmyking.dto.address.AddressResponse;
import com.banhmyking.banhmyking.entity.Address;

@Component
public class AddressMapper {

	public Address toEntity(AddressRequest request) {
		Address address = new Address();
		updateEntity(address, request);
		return address;
	}

	public void updateEntity(Address address, AddressRequest request) {
		address.setReceiverName(request.getReceiverName().trim());
		address.setReceiverPhone(request.getReceiverPhone().trim());
		address.setFullAddress(request.getFullAddress().trim());
		address.setDefaultAddress(request.isDefaultAddress());
	}

	public AddressResponse toResponse(Address address) {
		return AddressResponse.builder()
				.id(address.getId())
				.userId(address.getUser().getId())
				.receiverName(address.getReceiverName())
				.receiverPhone(address.getReceiverPhone())
				.fullAddress(address.getFullAddress())
				.defaultAddress(address.isDefaultAddress())
				.build();
	}
}
