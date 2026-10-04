package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.util.GeoUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Khoảng cách giao hàng do SERVER tính từ vị trí quán (admin ghim ở Cài đặt website)
 * tới toạ độ điểm giao — thay cho distanceKm client tự gửi (plan.md §2.2: khách gửi 0 để được ship rẻ).
 *
 * Quãng đường thật dài hơn đường chim bay nên nhân hệ số {@code delivery.road-factor}
 * (mặc định 1.3) — ước lượng, không gọi dịch vụ chỉ đường bên ngoài.
 */
@Service
@RequiredArgsConstructor
public class StoreDistanceService {

    private final SiteSettingService siteSettingService;

    @Value("${delivery.road-factor:1.3}")
    private double roadFactor = 1.3;

    /**
     * Quãng đường ước tính (km, 2 chữ số) từ quán tới điểm giao.
     * Rỗng khi điểm giao chưa có toạ độ hoặc quán chưa ghim vị trí → caller tính phí theo khu vực.
     *
     * @throws BusinessException điểm giao nằm ngoài bán kính phục vụ
     */
    public Optional<BigDecimal> roadDistanceKm(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        Map<String, String> settings = siteSettingService.getPublicSettings();
        Double storeLat = parse(settings.get(SiteSettingKeys.STORE_LATITUDE));
        Double storeLng = parse(settings.get(SiteSettingKeys.STORE_LONGITUDE));
        if (storeLat == null || storeLng == null) {
            return Optional.empty();
        }

        double km = GeoUtils.haversineKm(storeLat, storeLng, latitude.doubleValue(), longitude.doubleValue())
                * roadFactor;
        BigDecimal distance = BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);

        Double maxRadius = parse(settings.get(SiteSettingKeys.DELIVERY_MAX_RADIUS_KM));
        if (maxRadius != null && km > maxRadius) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, String.format(
                    "Địa chỉ cách cửa hàng khoảng %.1f km, ngoài bán kính giao hàng %.1f km",
                    km, maxRadius));
        }
        return Optional.of(distance);
    }

    private static Double parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
