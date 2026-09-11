package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.enums.DeliveryArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryFeeCalculatorTest {

    private DeliveryFeeCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DeliveryFeeCalculator();
        ReflectionTestUtils.setField(calculator, "baseDistanceKm", 2.0);
        ReflectionTestUtils.setField(calculator, "baseFee", BigDecimal.valueOf(15000));
        ReflectionTestUtils.setField(calculator, "feePerKm", BigDecimal.valueOf(5000));
        ReflectionTestUtils.setField(calculator, "urbanFee", BigDecimal.valueOf(15000));
        ReflectionTestUtils.setField(calculator, "suburbanFee", BigDecimal.valueOf(30000));
        ReflectionTestUtils.setField(calculator, "freeshipThreshold", BigDecimal.valueOf(200000));
    }

    @Test
    @DisplayName("AC 1: Khoảng cách <= 2km trong nội thành -> Phí cố định 15.000đ")
    void calculateFee_withinBaseDistance_shouldReturnBaseFee() {
        DeliveryFeeResult result = calculator.calculateFee(
                BigDecimal.valueOf(1.5),
                "Số 123 Phố Huế, Quận Hai Bà Trưng, Hà Nội",
                BigDecimal.valueOf(100000)
        );

        assertThat(result).isNotNull();
        assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(result.getArea()).isEqualTo(DeliveryArea.INNER_CITY);
        assertThat(result.isFreeship()).isFalse();
    }

    @Test
    @DisplayName("AC 1: Khoảng cách 3.2km (vượt 1.2km làm tròn lên 2km) -> 15.000 + 2*5.000 = 25.000đ")
    void calculateFee_overBaseDistance_shouldAddFeePerKmCeiled() {
        DeliveryFeeResult result = calculator.calculateFee(
                BigDecimal.valueOf(3.2),
                "Quận Cầu Giấy, Hà Nội",
                BigDecimal.valueOf(120000)
        );

        assertThat(result).isNotNull();
        assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(result.isFreeship()).isFalse();
    }

    @Test
    @DisplayName("AC 1: Địa chỉ ngoại thành -> Áp dụng sàn ngoại thành 30.000đ")
    void calculateFee_suburbanAddress_shouldApplySuburbanFloor() {
        DeliveryFeeResult result = calculator.calculateFee(
                null,
                "Xã Đa Tốn, Huyện Gia Lâm, Hà Nội",
                BigDecimal.valueOf(80000)
        );

        assertThat(result).isNotNull();
        assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(result.getArea()).isEqualTo(DeliveryArea.SUBURBAN);
        assertThat(result.isFreeship()).isFalse();
    }

    @Test
    @DisplayName("AC 1: Tổng đơn hàng đạt ngưỡng Freeship (>= 200.000đ) -> Phí ship về 0đ")
    void calculateFee_subtotalReachesThreshold_shouldBeFreeship() {
        DeliveryFeeResult result = calculator.calculateFee(
                BigDecimal.valueOf(4.0),
                "Quận Đống Đa, Hà Nội",
                BigDecimal.valueOf(250000)
        );

        assertThat(result).isNotNull();
        assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(result.isFreeship()).isTrue();
        assertThat(result.getDescription()).containsIgnoringCase("miễn phí giao hàng");
    }

    @Test
    @DisplayName("AC 1: Đơn hàng cận dưới ngưỡng freeship (199.000đ) -> Không freeship")
    void calculateFee_subtotalBelowThreshold_shouldNotBeFreeship() {
        DeliveryFeeResult result = calculator.calculateFee(
                BigDecimal.valueOf(1.0),
                "Quận Hoàn Kiếm, Hà Nội",
                BigDecimal.valueOf(199000)
        );

        assertThat(result).isNotNull();
        assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(result.isFreeship()).isFalse();
    }
}
