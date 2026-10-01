package com.banhmyking.banhmyking.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void samePointIsZeroKm() {
        assertThat(GeoUtils.haversineKm(21.0285, 105.8542, 21.0285, 105.8542)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(GeoUtils.haversineKm(21.0, 105.8, 22.0, 105.8)).isCloseTo(111.2, within(0.2));
    }

    @Test
    void hanoiToHoChiMinhCityIsAbout1140Km() {
        // Hồ Hoàn Kiếm → Chợ Bến Thành, đường chim bay ~1.140 km
        assertThat(GeoUtils.haversineKm(21.0287, 105.8524, 10.7725, 106.6980)).isCloseTo(1140, within(15.0));
    }
}
