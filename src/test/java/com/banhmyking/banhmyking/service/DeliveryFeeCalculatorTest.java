package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.delivery.DeliveryFeeResult;
import com.banhmyking.banhmyking.enums.DeliveryArea;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Nested
    @DisplayName("1. Tính phí theo khoảng cách & Biên làm tròn (Distance Boundaries)")
    class DistanceBoundaryTests {

        @Test
        @DisplayName("Khoảng cách 1.5km (< baseDistance 2km) nội thành -> Phí cơ bản 15.000đ")
        void calculateFee_underBaseDistance_shouldReturnBaseFee() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(1.5),
                    "Số 123 Phố Huế, Quận Hai Bà Trưng, Hà Nội",
                    BigDecimal.valueOf(100000)
            );

            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
            assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
            assertThat(result.getArea()).isEqualTo(DeliveryArea.INNER_CITY);
            assertThat(result.isFreeship()).isFalse();
        }

        @Test
        @DisplayName("Khoảng cách đúng bằng 2.0km (chạm biên baseDistance) -> Phí cơ bản 15.000đ")
        void calculateFee_exactBaseDistance_shouldReturnBaseFee() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(2.0),
                    "Quận Đống Đa, Hà Nội",
                    BigDecimal.valueOf(150000)
            );

            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
            assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        }

        @Test
        @DisplayName("Khoảng cách 2.0001km (vừa vượt 2km) -> ceil(0.0001km) = 1km -> 15.000 + 1*5.000 = 20.000đ")
        void calculateFee_justOverBaseDistance_shouldCeilToOneKm() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(2.0001),
                    "Quận Ba Đình, Hà Nội",
                    BigDecimal.valueOf(100000)
            );

            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        }

        @Test
        @DisplayName("Khoảng cách đúng bằng 3.0km (vượt đúng 1.0km) -> ceil(1.0km) = 1km -> 15.000 + 1*5.000 = 20.000đ")
        void calculateFee_exactOneKmOverBase_shouldAddSingleKmFee() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(3.0),
                    "Quận Hoàn Kiếm, Hà Nội",
                    BigDecimal.valueOf(100000)
            );

            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        }

        @Test
        @DisplayName("Khoảng cách 3.001km (vượt 1.001km) -> ceil(1.001km) = 2km -> 15.000 + 2*5.000 = 25.000đ")
        void calculateFee_justOverThreeKm_shouldCeilToTwoKm() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(3.001),
                    "Quận Cầu Giấy, Hà Nội",
                    BigDecimal.valueOf(100000)
            );

            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        }

        @Test
        @DisplayName("Khoảng cách xa 7.5km (vượt 5.5km) -> ceil(5.5km) = 6km -> 15.000 + 6*5.000 = 45.000đ")
        void calculateFee_longDistance_shouldCalculateAccurately() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(7.5),
                    "Quận Thanh Xuân, Hà Nội",
                    BigDecimal.valueOf(120000)
            );

            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(45000));
        }

        @Test
        @DisplayName("Khoảng cách null hoặc <= 0 -> Fallback tính theo khu vực nội thành (15.000đ)")
        void calculateFee_nullOrZeroDistance_shouldFallbackToAreaFee() {
            DeliveryFeeResult r1 = calculator.calculateFee(null, "Quận Hai Bà Trưng, Hà Nội", BigDecimal.valueOf(50000));
            assertThat(r1.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));

            DeliveryFeeResult r2 = calculator.calculateFee(BigDecimal.ZERO, "Quận Hai Bà Trưng, Hà Nội", BigDecimal.valueOf(50000));
            assertThat(r2.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));

            DeliveryFeeResult r3 = calculator.calculateFee(BigDecimal.valueOf(-1.5), "Quận Hai Bà Trưng, Hà Nội", BigDecimal.valueOf(50000));
            assertThat(r3.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        }
    }

    @Nested
    @DisplayName("2. Nhận diện khu vực và Sàn phí ngoại thành (Suburban Floor Rules)")
    class SuburbanAreaTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "Huyện Gia Lâm, Hà Nội",
                "Xã Kim Nỗ, Huyện Đông Anh, Hà Nội",
                "Thị trấn Sóc Sơn, Huyện Sóc Sơn",
                "Huyện Thanh Trì, Hà Nội",
                "Huyện Hoài Đức, Hà Nội",
                "Huyện Đan Phượng, Hà Nội",
                "Huyện Thạch Thất",
                "Huyện Quốc Oai",
                "Huyện Thường Tín",
                "Huyện Phú Xuyên",
                "Huyện Mê Linh",
                "Huyện Ba Vì",
                "Thị xã Sơn Tây",
                "Huyện Ứng Hòa",
                "Huyện Mỹ Đức"
        })
        @DisplayName("Nhận diện chính xác tất cả các huyện ngoại thành Hà Nội (có dấu)")
        void detectArea_hanoiSuburbanWithAccents(String address) {
            assertThat(calculator.detectArea(address)).isEqualTo(DeliveryArea.SUBURBAN);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "huyen hoc mon, tp ho chi minh",
                "Xã Bình Hưng, Huyện Bình Chánh, TPHCM",
                "Huyện Nhà Bè, TP.HCM",
                "Huyện Củ Chi",
                "Huyện Cần Giờ",
                "Thành phố Dĩ An, Tỉnh Bình Dương",
                "Thành phố Biên Hòa, Tỉnh Đồng Nai"
        })
        @DisplayName("Nhận diện chính xác các huyện ngoại thành TP.HCM và vùng ven")
        void detectArea_hcmcSuburbanAndSurroundings(String address) {
            assertThat(calculator.detectArea(address)).isEqualTo(DeliveryArea.SUBURBAN);
        }

        @Test
        @DisplayName("Nhận diện không phân biệt hoa thường và không dấu")
        void detectArea_caseInsensitiveAndUnaccented() {
            assertThat(calculator.detectArea("XA DA TON, HUYEN GIA LAM, HA NOI")).isEqualTo(DeliveryArea.SUBURBAN);
            assertThat(calculator.detectArea("dong anh ha noi")).isEqualTo(DeliveryArea.SUBURBAN);
            assertThat(calculator.detectArea("hoc mon sai gon")).isEqualTo(DeliveryArea.SUBURBAN);
        }

        @Test
        @DisplayName("Địa chỉ nội thành hoặc không xác định -> Nhận diện INNER_CITY")
        void detectArea_innerCity() {
            assertThat(calculator.detectArea("Quận Hoàn Kiếm, Hà Nội")).isEqualTo(DeliveryArea.INNER_CITY);
            assertThat(calculator.detectArea("Quận 1, TP Hồ Chí Minh")).isEqualTo(DeliveryArea.INNER_CITY);
            assertThat(calculator.detectArea("")).isEqualTo(DeliveryArea.INNER_CITY);
            assertThat(calculator.detectArea("   ")).isEqualTo(DeliveryArea.INNER_CITY);
            assertThat(calculator.detectArea(null)).isEqualTo(DeliveryArea.INNER_CITY);
        }

        @Test
        @DisplayName("Ngoại thành không có khoảng cách -> Áp dụng sàn ngoại thành 30.000đ")
        void calculateFee_suburbanWithoutDistance_shouldReturnSuburbanFloor() {
            DeliveryFeeResult result = calculator.calculateFee(null, "Huyện Gia Lâm, Hà Nội", BigDecimal.valueOf(100000));

            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(30000));
            assertThat(result.getArea()).isEqualTo(DeliveryArea.SUBURBAN);
        }

        @Test
        @DisplayName("Ngoại thành với khoảng cách gần (1.5km) -> Phí theo km là 15k nhưng sàn ngoại thành là 30k -> Lấy 30.000đ")
        void calculateFee_suburbanWithShortDistance_shouldTakeFloor() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(1.5),
                    "Huyện Hoài Đức, Hà Nội",
                    BigDecimal.valueOf(100000)
            );

            // baseFee = 15k, suburbanFee = 30k -> suburbanFee.max(baseFee) = 30k
            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        }

        @Test
        @DisplayName("Ngoại thành với khoảng cách trung bình 4km -> Phí theo km 25k < sàn 30k -> Lấy 30.000đ")
        void calculateFee_suburbanWithMediumDistance_shouldTakeFloor() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(4.0),
                    "Huyện Thanh Trì, Hà Nội",
                    BigDecimal.valueOf(100000)
            );

            // calculatedFee = 15k + 2*5k = 25k < 30k -> Lấy 30k
            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        }

        @Test
        @DisplayName("Ngoại thành với khoảng cách xa 10km -> Phí theo km 55k > sàn 30k -> Lấy 55.000đ")
        void calculateFee_suburbanWithLongDistance_shouldTakeCalculatedFee() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(10.0),
                    "Huyện Sóc Sơn, Hà Nội",
                    BigDecimal.valueOf(100000)
            );

            // calculatedFee = 15k + 8*5k = 55k > 30k -> Lấy 55k
            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(55000));
            assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(55000));
        }
    }

    @Nested
    @DisplayName("3. Ngưỡng Freeship (Freeship Threshold Boundary)")
    class FreeshipBoundaryTests {

        @Test
        @DisplayName("Đơn hàng thiếu 1đ (199.999đ < 200.000đ) -> Không freeship, thu đủ phí")
        void calculateFee_subtotalJustBelowThreshold_shouldNotFreeship() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(3.0),
                    "Quận Ba Đình, Hà Nội",
                    BigDecimal.valueOf(199999)
            );

            assertThat(result.isFreeship()).isFalse();
            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        }

        @Test
        @DisplayName("Đơn hàng vừa đúng 200.000đ (chạm ngưỡng) -> Kích hoạt freeship, phí = 0đ")
        void calculateFee_subtotalExactThreshold_shouldBeFreeship() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(3.0),
                    "Quận Ba Đình, Hà Nội",
                    BigDecimal.valueOf(200000)
            );

            assertThat(result.isFreeship()).isTrue();
            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(result.getDescription()).contains("miễn phí giao hàng");
        }

        @Test
        @DisplayName("Đơn hàng ngoại thành phí gốc 55.000đ đạt 250.000đ (> 200k) -> Phí ship về 0đ")
        void calculateFee_suburbanReachesThreshold_shouldBeFreeship() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(10.0),
                    "Huyện Đông Anh, Hà Nội",
                    BigDecimal.valueOf(250000)
            );

            assertThat(result.isFreeship()).isTrue();
            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getOriginalFee()).isEqualByComparingTo(BigDecimal.valueOf(55000));
            assertThat(result.getArea()).isEqualTo(DeliveryArea.SUBURBAN);
        }

        @Test
        @DisplayName("Subtotal null -> Không freeship")
        void calculateFee_nullSubtotal_shouldNotFreeship() {
            DeliveryFeeResult result = calculator.calculateFee(
                    BigDecimal.valueOf(2.0),
                    "Quận Hoàn Kiếm, Hà Nội",
                    null
            );

            assertThat(result.isFreeship()).isFalse();
            assertThat(result.getShippingFee()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        }
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
