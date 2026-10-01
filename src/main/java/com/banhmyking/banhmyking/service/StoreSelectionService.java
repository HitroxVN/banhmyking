package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.util.GeoUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Chấm điều kiện từng cơ sở cho một giỏ hàng + điểm giao (spec §3.1, §3.2, §3.4).
 * Server luôn chấm lại khi tạo đơn — không tin cơ sở/phí client gửi.
 */
@Service
public class StoreSelectionService {

    public enum Reason { CLOSED, NOT_ACCEPTING, OUT_OF_RADIUS, BELOW_MIN_ORDER, ITEM_UNAVAILABLE }

    public record Candidate(Store store, BigDecimal distanceKm, List<Reason> reasons, List<String> unavailableItems) {
        public boolean eligible() {
            return reasons.isEmpty();
        }
    }

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final StoreRepository storeRepository;
    private final InventoryService inventoryService;
    private final Clock clock;
    private final double roadFactor;

    public StoreSelectionService(StoreRepository storeRepository, InventoryService inventoryService, Clock clock,
                                 @Value("${delivery.road-factor:1.3}") double roadFactor) {
        this.storeRepository = storeRepository;
        this.inventoryService = inventoryService;
        this.clock = clock;
        this.roadFactor = roadFactor;
    }

    public List<Candidate> evaluate(BigDecimal latitude, BigDecimal longitude, BigDecimal subtotal,
                                    Map<Product, Integer> items) {
        boolean destinationPinned = latitude != null && longitude != null;
        LocalTime now = LocalTime.now(clock);
        List<Candidate> result = new ArrayList<>();
        for (Store store : storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()) {
            List<Reason> reasons = new ArrayList<>();
            BigDecimal distance = null;
            if (destinationPinned && store.hasLocation()) {
                double km = GeoUtils.haversineKm(store.getLatitude().doubleValue(), store.getLongitude().doubleValue(),
                        latitude.doubleValue(), longitude.doubleValue()) * roadFactor;
                distance = BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP);
                if (distance.compareTo(store.getDeliveryRadiusKm()) > 0) {
                    reasons.add(Reason.OUT_OF_RADIUS);
                }
            }
            if (!StoreHours.isOpen(store, now)) {
                reasons.add(0, Reason.CLOSED);
            } else if (!store.isAcceptingOrders()) {
                reasons.add(0, Reason.NOT_ACCEPTING);
            }
            if (subtotal != null && subtotal.compareTo(store.getMinOrderAmount()) < 0) {
                reasons.add(Reason.BELOW_MIN_ORDER);
            }
            List<String> unavailable = inventoryService.unavailableItems(store.getId(), items);
            if (!unavailable.isEmpty()) {
                reasons.add(Reason.ITEM_UNAVAILABLE);
            }
            result.add(new Candidate(store, distance, List.copyOf(reasons), unavailable));
        }
        result.sort(Comparator.comparing((Candidate c) -> !c.eligible())
                .thenComparing(Candidate::distanceKm, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(c -> c.store().getCode()));
        return result;
    }

    /** Đề xuất chỉ khi điểm giao có toạ độ — không có toạ độ thì khách tự chọn (spec §3.4). */
    public Optional<Candidate> recommend(List<Candidate> candidates) {
        if (candidates.isEmpty() || !candidates.get(0).eligible()) {
            return Optional.empty();
        }
        boolean anyDistance = candidates.stream().anyMatch(c -> c.distanceKm() != null);
        return anyDistance ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    public Candidate requireEligible(Long storeId, BigDecimal latitude, BigDecimal longitude, BigDecimal subtotal,
                                     Map<Product, Integer> items) {
        List<Candidate> candidates = evaluate(latitude, longitude, subtotal, items);
        if (storeId == null) {
            return recommend(candidates).orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                    candidates.stream().anyMatch(Candidate::eligible)
                            ? "Vui lòng chọn cơ sở phục vụ đơn hàng"
                            : "Hiện chưa có cơ sở nào phục vụ được địa chỉ và giỏ hàng này"));
        }
        Candidate chosen = candidates.stream().filter(c -> c.store().getId().equals(storeId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "Cơ sở đã chọn không còn hoạt động"));
        if (!chosen.eligible()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    chosen.store().getName() + ": " + describe(chosen.reasons().get(0), chosen));
        }
        return chosen;
    }

    public String describe(Reason reason, Candidate c) {
        Store s = c.store();
        return switch (reason) {
            case CLOSED -> "đang đóng cửa (mở " + s.getOpenTime().format(HH_MM) + "–" + s.getCloseTime().format(HH_MM) + ")";
            case NOT_ACCEPTING -> "tạm ngưng nhận đơn";
            case OUT_OF_RADIUS -> String.format("địa chỉ cách khoảng %.1f km, ngoài bán kính giao hàng %.1f km",
                    c.distanceKm().doubleValue(), s.getDeliveryRadiusKm().doubleValue());
            case BELOW_MIN_ORDER -> "đơn tối thiểu " + NumberFormat.getIntegerInstance(new Locale("vi", "VN"))
                    .format(s.getMinOrderAmount()) + "đ";
            case ITEM_UNAVAILABLE -> "tạm hết " + String.join(", ", c.unavailableItems());
        };
    }
}
