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
		address.setStreet(blankToNull(request.getStreet()));
		address.setWard(blankToNull(request.getWard()));
		address.setProvince(blankToNull(request.getProvince()));
		// Toạ độ đi theo cặp: thiếu một trong hai coi như chưa ghim, tránh điểm nửa vời.
		boolean pinned = request.getLatitude() != null && request.getLongitude() != null;
		address.setLatitude(pinned ? request.getLatitude() : null);
		address.setLongitude(pinned ? request.getLongitude() : null);
		address.setDefaultAddress(request.isDefaultAddress());
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public AddressResponse toResponse(Address address) {
		return AddressResponse.builder()
				.id(address.getId())
				.userId(address.getUser().getId())
				.receiverName(address.getReceiverName())
				.receiverPhone(address.getReceiverPhone())
				.fullAddress(address.getFullAddress())
				.street(address.getStreet())
				.ward(address.getWard())
				.province(address.getProvince())
				.latitude(address.getLatitude())
				.longitude(address.getLongitude())
				.defaultAddress(address.isDefaultAddress())
				.build();
	}
}
