package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.enums.DeliveryArea;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Component tính toán phí giao hàng theo khoảng cách, khu vực và kiểm tra điều kiện freeship.
 */
@Slf4j
@Getter
@Component
public class DeliveryFeeCalculator {

    @Value("${delivery.base-distance-km:2.0}")
    private double baseDistanceKm = 2.0;

    @Value("${delivery.base-fee:15000}")
    private BigDecimal baseFee = BigDecimal.valueOf(15000);

    @Value("${delivery.fee-per-km:5000}")
    private BigDecimal feePerKm = BigDecimal.valueOf(5000);

    @Value("${delivery.urban-fee:15000}")
    private BigDecimal urbanFee = BigDecimal.valueOf(15000);

    @Value("${delivery.suburban-fee:30000}")
    private BigDecimal suburbanFee = BigDecimal.valueOf(30000);

    @Value("${delivery.freeship-threshold:200000}")
    private BigDecimal freeshipThreshold = BigDecimal.valueOf(200000);

    private static final List<String> SUBURBAN_KEYWORDS = List.of(
            "gia lam", "dong anh", "soc son", "thanh tri", "hoai duc", "dan phuong",
            "thach that", "quoc oai", "thuong tin", "phu xuyen", "me linh", "ba vi",
            "son tay", "ung hoa", "my duc",
            "hoc mon", "binh chanh", "nha be", "cu chi", "can gio", "binh duong", "dong nai"
    );

    /**
     * Tính toán chi tiết phí giao hàng dựa trên khoảng cách, địa chỉ và tổng giá trị tạm tính.
     *
     * @param distanceKm       Khoảng cách giao hàng tính bằng km (tùy chọn)
     * @param shippingAddress  Địa chỉ nhận hàng (để nhận diện khu vực nội thành/ngoại thành)
     * @param subtotal         Giá trị tạm tính các món trong đơn hàng
     * @return DeliveryFeeResult Chứa phí thực tế, phí gốc, cờ freeship và mô tả
     */
    public DeliveryFeeResult calculateFee(BigDecimal distanceKm, String shippingAddress, BigDecimal subtotal) {
        DeliveryArea area = detectArea(shippingAddress);
        BigDecimal originalFee;

        if (distanceKm != null && distanceKm.compareTo(BigDecimal.ZERO) > 0) {
            double dist = distanceKm.doubleValue();
            if (dist <= baseDistanceKm) {
                originalFee = (area == DeliveryArea.SUBURBAN) ? suburbanFee.max(baseFee) : baseFee;
            } else {
                double extraKm = dist - baseDistanceKm;
                long ceilExtraKm = (long) Math.ceil(extraKm);
                BigDecimal extraFee = feePerKm.multiply(BigDecimal.valueOf(ceilExtraKm));
                BigDecimal calculatedFee = baseFee.add(extraFee);
                originalFee = (area == DeliveryArea.SUBURBAN) ? calculatedFee.max(suburbanFee) : calculatedFee;
            }
        } else {
            // Không có khoảng cách cụ thể: tính theo khu vực
            originalFee = (area == DeliveryArea.SUBURBAN) ? suburbanFee : urbanFee;
        }

        originalFee = originalFee.setScale(2, RoundingMode.HALF_UP);

        // Kiểm tra ngưỡng Freeship
        boolean isFreeship = false;
        BigDecimal finalFee = originalFee;
        String description;

        if (subtotal != null && subtotal.compareTo(freeshipThreshold) >= 0) {
            isFreeship = true;
            finalFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            description = String.format("Đơn hàng đạt từ %,.0fđ được miễn phí giao hàng", freeshipThreshold.doubleValue());
        } else {
            String areaName = (area == DeliveryArea.SUBURBAN) ? "Ngoại thành" : "Nội thành";
            if (distanceKm != null && distanceKm.compareTo(BigDecimal.ZERO) > 0) {
                description = String.format("Phí giao hàng %.1f km (%s): %,.0fđ",
                        distanceKm.doubleValue(), areaName, originalFee.doubleValue());
            } else {
                description = String.format("Phí giao hàng khu vực %s: %,.0fđ", areaName, originalFee.doubleValue());
            }
        }

        log.debug("Calculated delivery fee: finalFee={}, originalFee={}, isFreeship={}, area={}, distanceKm={}",
                finalFee, originalFee, isFreeship, area, distanceKm);

        return DeliveryFeeResult.builder()
                .shippingFee(finalFee)
                .originalFee(originalFee)
                .distanceKm(distanceKm)
                .area(area)
                .freeship(isFreeship)
                .freeshipThreshold(freeshipThreshold.setScale(2, RoundingMode.HALF_UP))
                .description(description)
                .build();
    }

    /**
     * Nhận diện phân vùng khu vực (Nội thành / Ngoại thành) qua từ khóa trong địa chỉ.
     */
    public DeliveryArea detectArea(String address) {
        if (address == null || address.trim().isEmpty()) {
            return DeliveryArea.INNER_CITY;
        }

        String normalized = removeAccents(address.toLowerCase(Locale.ROOT));
        for (String keyword : SUBURBAN_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return DeliveryArea.SUBURBAN;
            }
        }

        return DeliveryArea.INNER_CITY;
    }

    private String removeAccents(String text) {
        String nfd = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(nfd).replaceAll("").replace('đ', 'd').replace('Đ', 'd');
    }
}
