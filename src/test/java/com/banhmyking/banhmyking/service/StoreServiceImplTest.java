package com.banhmyking.banhmyking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.impl.StoreServiceImpl;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreServiceImplTest {

    @Mock private StoreRepository storeRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderRepository orderRepository;

    private StoreServiceImpl service;

    /** 10:00 sáng giờ VN */
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @BeforeEach
    void setUp() {
        service = new StoreServiceImpl(storeRepository, userRepository, orderRepository, new StoreAccessGuard(), clock);
    }

    private StoreRequest request(String code) {
        return new StoreRequest(code, "Cơ sở Cầu Giấy", "12 Trần Thái Tông, Hà Nội", "0901000001",
                new BigDecimal("21.033300"), new BigDecimal("105.792000"), "06:30", "22:00",
                new BigDecimal("5"), new BigDecimal("3"), new BigDecimal("50000"), true);
    }

    @Test
    void createNormalisesCodeAndRejectsDuplicates() {
        when(storeRepository.findByCode("CS02")).thenReturn(Optional.empty());
        when(storeRepository.saveAndFlush(any(Store.class))).thenAnswer(inv -> {
            Store s = inv.getArgument(0);
            s.setId(2L);
            return s;
        });

        StoreResponse created = service.create(request(" cs02 "));

        assertThat(created.getCode()).isEqualTo("CS02");
        assertThat(created.isOpenNow()).isTrue();

        Store existing = new Store();
        existing.setCode("CS02");
        when(storeRepository.findByCode("CS02")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.create(request("CS02")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("đã tồn tại");
    }

    @Test
    void createRejectsCodeOfSoftDeletedStoreAsBusinessError() {
        Store deleted = new Store();
        deleted.setId(7L);
        deleted.setCode("CS07");
        deleted.setDeleted(true);
        when(storeRepository.findByCode("CS07")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.create(request("cs07")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cơ sở đã xoá")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
        verify(storeRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsCodeOfSoftDeletedStore() {
        Store current = new Store();
        current.setId(3L);
        current.setCode("CS03");
        Store deleted = new Store();
        deleted.setId(7L);
        deleted.setCode("CS07");
        deleted.setDeleted(true);
        when(storeRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(current));
        when(storeRepository.findByCode("CS07")).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.update(3L, request("CS07")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cơ sở đã xoá");
        assertThat(current.getCode()).isEqualTo("CS03");
    }

    @Test
    void updateKeepingSameCodeSkipsUniquenessCheck() {
        Store current = new Store();
        current.setId(3L);
        current.setCode("CS03");
        when(storeRepository.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(current));
        when(storeRepository.saveAndFlush(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.update(3L, request("cs03")).getCode()).isEqualTo("CS03");
        verify(storeRepository, never()).findByCode(any());
    }

    @Test
    void uniqueKeyRaceIsTranslatedToBusinessError() {
        when(storeRepository.findByCode("CS08")).thenReturn(Optional.empty());
        when(storeRepository.saveAndFlush(any(Store.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'CS08' for key 'uk_stores_code'"));

        assertThatThrownBy(() -> service.create(request("CS08")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("CS08");
    }

    @Test
    void rejectsOpenTimeNotBeforeCloseTime() {
        StoreRequest bad = new StoreRequest("CS09", "X", "Y địa chỉ", null, null, null, "22:00", "06:30",
                new BigDecimal("5"), new BigDecimal("3"), BigDecimal.ZERO, true);
        assertThatThrownBy(() -> service.create(bad))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Giờ mở cửa phải trước giờ đóng cửa");
    }

    @Test
    void deleteRefusedWhileStaffOrOpenOrdersRemain() {
        Store store = new Store();
        store.setId(4L);
        when(storeRepository.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(store));
        when(userRepository.countByStoreIdAndDeletedFalse(4L)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(4L)).hasMessageContaining("còn nhân viên");
        verify(storeRepository, never()).save(any());

        when(userRepository.countByStoreIdAndDeletedFalse(4L)).thenReturn(0L);
        when(orderRepository.countByStoreIdAndStatusIn(any(), anyList())).thenReturn(1L);
        assertThatThrownBy(() -> service.delete(4L)).hasMessageContaining("đơn chưa kết thúc");
    }

    @Test
    void managerTogglesOnlyOwnStore() {
        Store own = new Store();
        own.setId(1L);
        User manager = new User();
        manager.setId(50L);
        manager.setRole(RoleName.MANAGER);
        manager.setStore(own);
        when(userRepository.findById(50L)).thenReturn(Optional.of(manager));
        when(storeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(own));
        when(storeRepository.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.setAcceptingOrders(50L, 1L, false).isAcceptingOrders()).isFalse();
        assertThatThrownBy(() -> service.setAcceptingOrders(50L, 2L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
