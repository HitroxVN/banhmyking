package com.banhmyking.banhmyking.controller.address;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.banhmyking.banhmyking.dto.address.AddressRequest;
import com.banhmyking.banhmyking.dto.address.AddressResponse;
import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.service.AddressService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
@Tag(name = "Address", description = "APIs quản lý địa chỉ giao hàng")
public class AddressController {

	private final AddressService addressService;

	@GetMapping
	@Operation(summary = "Lấy danh sách địa chỉ")
	public ResponseEntity<ApiResponse<List<AddressResponse>>> getAddresses(
			@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
		return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách địa chỉ thành công", addressService.getAddresses(userId)));
	}

	@GetMapping("/{addressId}")
	@Operation(summary = "Lấy chi tiết địa chỉ")
	public ResponseEntity<ApiResponse<AddressResponse>> getAddress(
			@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
			@PathVariable Long addressId) {
		return ResponseEntity.ok(ApiResponse.ok("Lấy địa chỉ thành công", addressService.getAddress(userId, addressId)));
	}

	@PostMapping
	@Operation(summary = "Tạo địa chỉ")
	public ResponseEntity<ApiResponse<AddressResponse>> createAddress(
			@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
			@Valid @RequestBody AddressRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok("Tạo địa chỉ thành công", addressService.createAddress(userId, request)));
	}

	@PutMapping("/{addressId}")
	@Operation(summary = "Cập nhật địa chỉ")
	public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
			@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
			@PathVariable Long addressId,
			@Valid @RequestBody AddressRequest request) {
		return ResponseEntity.ok(ApiResponse.ok("Cập nhật địa chỉ thành công",
				addressService.updateAddress(userId, addressId, request)));
	}

	@DeleteMapping("/{addressId}")
	@Operation(summary = "Xóa địa chỉ")
	public ResponseEntity<ApiResponse<Void>> deleteAddress(
			@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
			@PathVariable Long addressId) {
		addressService.deleteAddress(userId, addressId);
		return ResponseEntity.ok(ApiResponse.ok("Xóa địa chỉ thành công"));
	}

	@PatchMapping("/{addressId}/default")
	@Operation(summary = "Đặt địa chỉ mặc định")
	public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
			@RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId,
			@PathVariable Long addressId) {
		return ResponseEntity.ok(ApiResponse.ok("Đặt địa chỉ mặc định thành công",
				addressService.setDefaultAddress(userId, addressId)));
	}
}
