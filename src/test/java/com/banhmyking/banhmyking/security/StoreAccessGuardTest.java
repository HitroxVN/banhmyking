package com.banhmyking.banhmyking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

class StoreAccessGuardTest {

    private final StoreAccessGuard guard = new StoreAccessGuard();

    private static Store store(long id) {
        Store s = new Store();
        s.setId(id);
        return s;
    }

    private static User user(RoleName role, Store store) {
        User u = new User();
        u.setId(99L);
        u.setRole(role);
        u.setStore(store);
        return u;
    }

    private static Order order(Store store) {
        Order o = new Order();
        o.setOrderCode("BMK-TEST");
        o.setStore(store);
        return o;
    }

    @Test
    void adminIsUnscopedAndMayFilterAnyStore() {
        User admin = user(RoleName.ADMIN, null);
        assertThat(guard.scopedStoreId(admin)).isNull();
        assertThat(guard.resolveStoreFilter(admin, null)).isNull();
        assertThat(guard.resolveStoreFilter(admin, 2L)).isEqualTo(2L);
        assertThatCode(() -> guard.requireOrderAccess(admin, order(store(2)))).doesNotThrowAnyException();
    }

    @Test
    void staffIsScopedToOwnStore() {
        User staff = user(RoleName.STAFF, store(1));
        assertThat(guard.scopedStoreId(staff)).isEqualTo(1L);
        assertThat(guard.resolveStoreFilter(staff, null)).isEqualTo(1L);
        assertThat(guard.resolveStoreFilter(staff, 1L)).isEqualTo(1L);
        assertThatThrownBy(() -> guard.resolveStoreFilter(staff, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void managerCannotTouchOrderOfAnotherStore() {
        User manager = user(RoleName.MANAGER, store(1));
        assertThatCode(() -> guard.requireOrderAccess(manager, order(store(1)))).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireOrderAccess(manager, order(store(2))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staffWithoutStoreIsRejected() {
        assertThatThrownBy(() -> guard.scopedStoreId(user(RoleName.STAFF, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("chưa được gán cơ sở");
    }

    @Test
    void customerIsNotOperator() {
        User customer = user(RoleName.CUSTOMER, null);
        assertThat(guard.isOperator(customer)).isFalse();
        assertThatThrownBy(() -> guard.requireOperator(customer)).isInstanceOf(BusinessException.class);
        assertThatCode(() -> guard.requireOrderAccess(customer, order(store(5)))).doesNotThrowAnyException();
    }

    @Test
    void storeAccess() {
        assertThatCode(() -> guard.requireStoreAccess(user(RoleName.ADMIN, null), 7L)).doesNotThrowAnyException();
        assertThatCode(() -> guard.requireStoreAccess(user(RoleName.STAFF, store(7)), 7L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireStoreAccess(user(RoleName.MANAGER, store(7)), 8L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> guard.requireStoreAccess(user(RoleName.SHIPPER, store(7)), 7L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void customerHasNoStoreScopeAndNoStoreAccess() {
        User customer = user(RoleName.CUSTOMER, null);
        assertThatThrownBy(() -> guard.scopedStoreId(customer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Khách hàng")
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> guard.resolveStoreFilter(customer, null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> guard.requireStoreAccess(customer, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void shipperWithoutStoreIsRejectedWithForbiddenCode() {
        User shipper = user(RoleName.SHIPPER, null);
        assertThatThrownBy(() -> guard.scopedStoreId(shipper))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> guard.requireStoreAccess(shipper, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(guard.sameStore(shipper, store(1))).isFalse();
    }

    @Test
    void staffWithoutStoreGetsForbiddenErrorCode() {
        assertThatThrownBy(() -> guard.scopedStoreId(user(RoleName.MANAGER, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatThrownBy(() -> guard.requireStoreAccess(user(RoleName.STAFF, null), 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void orderWithNullStoreIsHiddenFromStaffButVisibleToAdmin() {
        Order orphan = order(null);
        assertThatThrownBy(() -> guard.requireOrderAccess(user(RoleName.STAFF, store(1)), orphan))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("BMK-TEST");
        assertThatThrownBy(() -> guard.requireOrderAccess(user(RoleName.MANAGER, store(1)), orphan))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatCode(() -> guard.requireOrderAccess(user(RoleName.ADMIN, null), orphan)).doesNotThrowAnyException();
        assertThat(guard.sameStore(user(RoleName.STAFF, store(1)), null)).isFalse();
    }

    @Test
    void requireOperatorUsesForbiddenCode() {
        assertThatThrownBy(() -> guard.requireOperator(user(RoleName.SHIPPER, store(1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThatCode(() -> guard.requireOperator(user(RoleName.MANAGER, store(1)))).doesNotThrowAnyException();
    }
}
