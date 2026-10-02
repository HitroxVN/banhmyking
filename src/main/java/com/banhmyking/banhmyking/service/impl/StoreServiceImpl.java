package com.banhmyking.banhmyking.service.impl;

import com.banhmyking.banhmyking.dto.store.PublicStoreResponse;
import com.banhmyking.banhmyking.dto.store.StoreRequest;
import com.banhmyking.banhmyking.dto.store.StoreResponse;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.repository.OrderRepository;
import com.banhmyking.banhmyking.repository.StoreRepository;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.StoreAccessGuard;
import com.banhmyking.banhmyking.service.StoreHours;
import com.banhmyking.banhmyking.service.StoreService;
import java.time.Clock;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final List<OrderStatus> OPEN_STATUSES = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED,
            OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERING);

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final StoreAccessGuard storeAccessGuard;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<StoreResponse> listAll() {
        return storeRepository.findByDeletedFalseOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicStoreResponse> listPublic() {
        LocalTime now = LocalTime.now(clock);
        return storeRepository.findByActiveTrueAndDeletedFalseOrderByCodeAsc().stream()
                .map(s -> new PublicStoreResponse(s.getId(), s.getCode(), s.getName(), s.getAddress(), s.getPhone(),
                        s.getLatitude(), s.getLongitude(), s.getOpenTime().format(HH_MM),
                        s.getCloseTime().format(HH_MM), StoreHours.isOpen(s, now), s.isAcceptingOrders()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StoreResponse get(Long id) {
        return toResponse(requireStore(id));
    }

    @Override
    @Transactional
    public StoreResponse create(StoreRequest request) {
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        requireCodeAvailable(code);
        Store store = new Store();
        store.setCode(code);
        apply(store, request);
        return toResponse(saveCheckingCode(store));
    }

    @Override
    @Transactional
    public StoreResponse update(Long id, StoreRequest request) {
        Store store = requireStore(id);
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (!code.equals(store.getCode())) {
            requireCodeAvailable(code);
        }
        store.setCode(code);
        apply(store, request);
        return toResponse(saveCheckingCode(store));
    }

    /**
     * uk_stores_code phủ cả cơ sở đã xoá mềm (V11) → mã của cơ sở đã xoá KHÔNG dùng lại được.
     * Kiểm tra trên mọi dòng để trả lỗi nghiệp vụ rõ ràng thay vì để INSERT/UPDATE nổ ràng buộc.
     */
    private void requireCodeAvailable(String code) {
        storeRepository.findByCode(code).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, existing.isDeleted()
                    ? "Mã cơ sở " + code + " đã được dùng bởi một cơ sở đã xoá — vui lòng chọn mã khác"
                    : "Mã cơ sở " + code + " đã tồn tại");
        });
    }

    /** Lưu + flush ngay để vi phạm uk_stores_code (2 request trùng mã cùng lúc) thành lỗi nghiệp vụ, không 500/409 thô. */
    private Store saveCheckingCode(Store store) {
        try {
            return storeRepository.saveAndFlush(store);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Mã cơ sở " + store.getCode() + " đã tồn tại");
        }
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Store store = requireStore(id);
        if (userRepository.countByStoreIdAndDeletedFalse(id) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cơ sở còn nhân viên — chuyển nhân viên sang cơ sở khác trước khi xoá");
        }
        if (orderRepository.countByStoreIdAndStatusIn(id, OPEN_STATUSES) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Cơ sở còn đơn chưa kết thúc — không thể xoá");
        }
        store.setDeleted(true);
        store.setActive(false);
        storeRepository.save(store);
    }

    @Override
    @Transactional
    public StoreResponse setAcceptingOrders(Long actorId, Long storeId, boolean accepting) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException(NotFoundMessages.userById(actorId)));
        storeAccessGuard.requireStoreAccess(actor, storeId);
        Store store = requireStore(storeId);
        store.setAcceptingOrders(accepting);
        return toResponse(storeRepository.save(store));
    }

    @Override
    @Transactional(readOnly = true)
    public Store requireActiveStore(Long id) {
        Store store = requireStore(id);
        if (!store.isActive()) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + id);
        }
        return store;
    }

    private Store requireStore(Long id) {
        return storeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + id));
    }

    private void apply(Store store, StoreRequest request) {
        LocalTime open = LocalTime.parse(request.openTime());
        LocalTime close = LocalTime.parse(request.closeTime());
        if (!open.isBefore(close)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giờ mở cửa phải trước giờ đóng cửa");
        }
        boolean pinned = request.latitude() != null && request.longitude() != null;
        store.setName(request.name().trim());
        store.setAddress(request.address().trim());
        store.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        store.setLatitude(pinned ? request.latitude() : null);
        store.setLongitude(pinned ? request.longitude() : null);
        store.setOpenTime(open);
        store.setCloseTime(close);
        store.setDeliveryRadiusKm(request.deliveryRadiusKm());
        store.setFreeShipRadiusKm(request.freeShipRadiusKm());
        store.setMinOrderAmount(request.minOrderAmount());
        store.setActive(request.active());
    }

    private StoreResponse toResponse(Store s) {
        return StoreResponse.builder()
                .id(s.getId()).code(s.getCode()).name(s.getName()).address(s.getAddress()).phone(s.getPhone())
                .latitude(s.getLatitude()).longitude(s.getLongitude())
                .openTime(s.getOpenTime().format(HH_MM)).closeTime(s.getCloseTime().format(HH_MM))
                .acceptingOrders(s.isAcceptingOrders()).deliveryRadiusKm(s.getDeliveryRadiusKm())
                .freeShipRadiusKm(s.getFreeShipRadiusKm()).minOrderAmount(s.getMinOrderAmount())
                .active(s.isActive()).openNow(StoreHours.isOpen(s, LocalTime.now(clock)))
                .staffCount(s.getId() == null ? 0 : userRepository.countByStoreIdAndDeletedFalse(s.getId()))
                .build();
    }
}
