package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.util.GeoUtils;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreDistanceServiceTest {

    // Quán ở Hồ Hoàn Kiếm; điểm giao ở Láng Hạ (~4 km chim bay)
    private static final String STORE_LAT = "21.028700";
    private static final String STORE_LNG = "105.852400";
    private static final BigDecimal DEST_LAT = new BigDecimal("21.016000");
    private static final BigDecimal DEST_LNG = new BigDecimal("105.814000");

    @Mock
    private SiteSettingService siteSettingService;

    @InjectMocks
    private StoreDistanceService storeDistanceService;

    private void settings(String lat, String lng, String radius) {
        Map<String, String> map = new HashMap<>(SiteSettingKeys.DEFAULTS);
        map.put(SiteSettingKeys.STORE_LATITUDE, lat);
        map.put(SiteSettingKeys.STORE_LONGITUDE, lng);
        map.put(SiteSettingKeys.DELIVERY_MAX_RADIUS_KM, radius);
        when(siteSettingService.getPublicSettings()).thenReturn(map);
    }

    @Test
    void destinationWithoutCoordinates_isEmpty_andSkipsSettingsLookup() {
        assertThat(storeDistanceService.roadDistanceKm(null, DEST_LNG)).isEmpty();
        verifyNoInteractions(siteSettingService);
    }

    @Test
    void storeNotPinnedYet_isEmpty() {
        settings("", "", "10");

        assertThat(storeDistanceService.roadDistanceKm(DEST_LAT, DEST_LNG)).isEmpty();
    }

    @Test
    void withinRadius_returnsStraightLineTimesRoadFactor() {
        settings(STORE_LAT, STORE_LNG, "10");
        double expected = GeoUtils.haversineKm(21.0287, 105.8524, 21.016, 105.814) * 1.3;

        BigDecimal km = storeDistanceService.roadDistanceKm(DEST_LAT, DEST_LNG).orElseThrow();

        assertThat(km.doubleValue()).isCloseTo(expected, org.assertj.core.api.Assertions.within(0.01));
        assertThat(km.scale()).isEqualTo(2);
    }

    @Test
    void beyondRadius_isRejected() {
        settings(STORE_LAT, STORE_LNG, "2");

        assertThatThrownBy(() -> storeDistanceService.roadDistanceKm(DEST_LAT, DEST_LNG))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngoài bán kính giao hàng 2.0 km");
    }

    @Test
    void blankRadius_meansNoLimit() {
        settings(STORE_LAT, STORE_LNG, "");

        assertThat(storeDistanceService.roadDistanceKm(new BigDecimal("10.7725"), new BigDecimal("106.6980")))
                .isPresent();
    }
}
