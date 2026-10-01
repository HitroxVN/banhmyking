package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.dto.common.ApiResponse;
import com.banhmyking.banhmyking.dto.delivery.CalculateDeliveryFeeRequest;
import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.service.DeliveryFeeCalculator;
import com.banhmyking.banhmyking.service.StoreDistanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Tag(name = "Delivery & Shipping", description = "APIs tính toán phí giao hàng và chính sách Freeship")
public class DeliveryController {

    private final DeliveryFeeCalculator deliveryFeeCalculator;
    private final StoreDistanceService storeDistanceService;

    @GetMapping("/fee")
    @Operation(summary = "Tính thử phí giao hàng (Query Params)",
            description = "Có toạ độ điểm giao và quán đã ghim vị trí → server tự tính khoảng cách; "
                    + "không thì tính theo khu vực (nội/ngoại thành). Xét freeship nếu đơn đạt ngưỡng.")
    public ResponseEntity<ApiResponse<DeliveryFeeResult>> getDeliveryFee(
            @Parameter(description = "Vĩ độ điểm giao", example = "21.028511")
            @RequestParam(required = false) BigDecimal latitude,
            @Parameter(description = "Kinh độ điểm giao", example = "105.804817")
            @RequestParam(required = false) BigDecimal longitude,
            @Parameter(description = "Địa chỉ nhận hàng chi tiết", example = "123 Lê Lợi, Quận 1, TP.HCM")
            @RequestParam(required = false) String shippingAddress,
            @Parameter(description = "Tổng giá trị tạm tính các món (subtotal)", example = "150000")
            @RequestParam(required = false) BigDecimal subtotal) {

        BigDecimal distanceKm = storeDistanceService.roadDistanceKm(latitude, longitude).orElse(null);
        DeliveryFeeResult result = deliveryFeeCalculator.calculateFee(distanceKm, shippingAddress, subtotal);
        return ResponseEntity.ok(ApiResponse.ok("Tính phí giao hàng thành công", result));
    }

    @PostMapping("/fee")
    @Operation(summary = "Tính thử phí giao hàng (Request Body)",
            description = "Tính phí ship qua request body JSON.")
    public ResponseEntity<ApiResponse<DeliveryFeeResult>> calculateDeliveryFee(
            @Valid @RequestBody(required = false) CalculateDeliveryFeeRequest request) {

        BigDecimal distanceKm = request == null ? null
                : storeDistanceService.roadDistanceKm(request.getLatitude(), request.getLongitude()).orElse(null);
        String address = request != null ? request.getShippingAddress() : null;
        BigDecimal subtotal = request != null ? request.getSubtotal() : null;

        DeliveryFeeResult result = deliveryFeeCalculator.calculateFee(distanceKm, address, subtotal);
        return ResponseEntity.ok(ApiResponse.ok("Tính phí giao hàng thành công", result));
    }
}
