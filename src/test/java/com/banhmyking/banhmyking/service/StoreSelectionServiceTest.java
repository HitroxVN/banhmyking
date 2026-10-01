package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.entity.Product;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.service.StoreSelectionService.Candidate;
import com.banhmyking.banhmyking.service.StoreSelectionService.Reason;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreSelectionServiceTest {

    // Điểm giao: gần Hồ Gươm
    private static final BigDecimal LAT = new BigDecimal("21.028700");
    private static final BigDecimal LNG = new BigDecimal("105.852400");

    @Mock private StoreRepository storeRepository;
    @Mock private InventoryService inventoryService;

    private StoreSelectionService service;
    private final Map<Product, Integer> items = Map.of(new Product(), 1);

    /** 10:00 sáng giờ VN */
    private final Clock tenAm = Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void setUp() {
        service = new StoreSelectionService(storeRepository, inventoryService, tenAm, 1.3);
        lenient().when(inventoryService.unavailableItems(anyLong(), anyMap())).thenReturn(List.of());
    }

    private static Store store(long id, String code, String lat, String lng) {
        Store s = new Store();
        s.setId(id);
        s.setCode(code);
        s.setName("Cơ sở " + code);
        s.setLatitude(lat == null ? null : new BigDecimal(lat));
        s.setLongitude(lng == null ? null : new BigDecimal(lng));
        return s;
    }

    @Test
    void recommendsNearestEligibleStore() {
        Store near = store(1, "CS01", "21.030000", "105.850000");   // ~0.3 km
        Store far = store(2, "CS02", "21.016000", "105.814000");    // ~5.4 km theo đường
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(far, near));

        List<Candidate> result = service.evaluate(LAT, LNG, new BigDecimal("60000"), items);

        assertThat(result.get(0).store().getCode()).isEqualTo("CS01");
        assertThat(service.recommend(result)).map(c -> c.store().getCode()).contains("CS01");
        assertThat(result.get(1).reasons()).containsExactly(Reason.OUT_OF_RADIUS);
    }

    @Test
    void closedNotAcceptingAndBelowMinOrderAreReported() {
        Store closed = store(1, "CS01", "21.030000", "105.850000");
        closed.setOpenTime(LocalTime.of(11, 0));
        Store paused = store(2, "CS02", "21.029000", "105.851000");
        paused.setAcceptingOrders(false);
        Store minOrder = store(3, "CS03", "21.028000", "105.852000");
        minOrder.setMinOrderAmount(new BigDecimal("50000"));
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(closed, paused, minOrder));

        List<Candidate> result = service.evaluate(LAT, LNG, new BigDecimal("30000"), items);

        assertThat(result).allMatch(c -> !c.eligible());
        assertThat(result).filteredOn(c -> c.store().getCode().equals("CS01")).first()
                .extracting(Candidate::reasons).asList().containsExactly(Reason.CLOSED);
        assertThat(result).filteredOn(c -> c.store().getCode().equals("CS02")).first()
                .extracting(Candidate::reasons).asList().containsExactly(Reason.NOT_ACCEPTING);
        assertThat(result).filteredOn(c -> c.store().getCode().equals("CS03")).first()
                .extracting(Candidate::reasons).asList().containsExactly(Reason.BELOW_MIN_ORDER);
        assertThat(service.recommend(result)).isEmpty();
    }

    @Test
    void soldOutItemMakesStoreIneligible() {
        Store s = store(1, "CS01", "21.030000", "105.850000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(s));
        when(inventoryService.unavailableItems(1L, items)).thenReturn(List.of("Bánh mì pate"));

        Candidate c = service.evaluate(LAT, LNG, new BigDecimal("60000"), items).get(0);

        assertThat(c.reasons()).containsExactly(Reason.ITEM_UNAVAILABLE);
        assertThat(c.unavailableItems()).containsExactly("Bánh mì pate");
    }

    @Test
    void storeWithoutLocationServesEverywhereButRanksLast() {
        Store unpinned = store(1, "CS01", null, null);
        Store pinned = store(2, "CS02", "21.030000", "105.850000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(unpinned, pinned));

        List<Candidate> result = service.evaluate(LAT, LNG, new BigDecimal("60000"), items);

        assertThat(result).extracting(c -> c.store().getCode()).containsExactly("CS02", "CS01");
        assertThat(result.get(1).eligible()).isTrue();
        assertThat(result.get(1).distanceKm()).isNull();
    }

    @Test
    void destinationWithoutCoordinatesHasNoRecommendationAndRequiresChoice() {
        Store s = store(1, "CS01", "21.030000", "105.850000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(s));

        List<Candidate> result = service.evaluate(null, null, new BigDecimal("60000"), items);

        assertThat(result.get(0).eligible()).isTrue();
        assertThat(service.recommend(result)).isEmpty();
        assertThatThrownBy(() -> service.requireEligible(null, null, null, new BigDecimal("60000"), items))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chọn cơ sở");
        assertThat(service.requireEligible(1L, null, null, new BigDecimal("60000"), items).store().getId()).isEqualTo(1L);
    }

    @Test
    void requireEligibleRejectsChosenStoreWithReason() {
        Store far = store(2, "CS02", "21.016000", "105.814000");
        when(storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc()).thenReturn(List.of(far));

        assertThatThrownBy(() -> service.requireEligible(2L, LAT, LNG, new BigDecimal("60000"), items))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ngoài bán kính giao hàng");
    }
}
