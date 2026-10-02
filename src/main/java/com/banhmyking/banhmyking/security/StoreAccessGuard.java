package com.banhmyking.banhmyking.security;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.Store;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import com.banhmyking.banhmyking.exception.NotFoundMessages;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * Một chỗ duy nhất quyết định "người này được đụng tới cơ sở nào" (spec §4).
 * Ngoài phạm vi trả 404 thay vì 403 — không để lộ đơn/cơ sở khác có tồn tại.
 */
@Component
public class StoreAccessGuard {

    public boolean isOperator(User actor) {
        RoleName role = actor.getRole();
        return role == RoleName.STAFF || role == RoleName.MANAGER || role == RoleName.ADMIN;
    }

    public void requireOperator(User actor) {
        if (!isOperator(actor)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Chỉ nhân viên, quản lý cơ sở hoặc quản trị viên mới có quyền thực hiện");
        }
    }

    /** ADMIN → null (không giới hạn); STAFF/MANAGER/SHIPPER → cơ sở của mình. */
    public Long scopedStoreId(User actor) {
        RoleName role = actor.getRole();
        if (role == RoleName.ADMIN) {
            return null;
        }
        if (role == RoleName.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Khách hàng không thuộc cơ sở nào");
        }
        if (actor.getStore() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Tài khoản chưa được gán cơ sở");
        }
        return actor.getStore().getId();
    }

    /** ADMIN lọc theo cơ sở yêu cầu (null = tất cả); vai trò khác luôn bị khoá về cơ sở mình. */
    public Long resolveStoreFilter(User actor, Long requestedStoreId) {
        Long own = scopedStoreId(actor);
        if (own == null) {
            return requestedStoreId;
        }
        if (requestedStoreId != null && !requestedStoreId.equals(own)) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + requestedStoreId);
        }
        return own;
    }

    public void requireStoreAccess(User actor, Long storeId) {
        RoleName role = actor.getRole();
        if (role == RoleName.ADMIN) {
            return;
        }
        boolean ownStore = (role == RoleName.STAFF || role == RoleName.MANAGER)
                && actor.getStore() != null && actor.getStore().getId().equals(storeId);
        if (!ownStore) {
            throw new ResourceNotFoundException("Không tìm thấy cơ sở với ID: " + storeId);
        }
    }

    /** Chỉ khoá STAFF/MANAGER theo cơ sở; khách/shipper đã có kiểm tra chủ đơn/được gán riêng. */
    public void requireOrderAccess(User actor, Order order) {
        RoleName role = actor.getRole();
        if (role != RoleName.STAFF && role != RoleName.MANAGER) {
            return;
        }
        if (!sameStore(actor, order.getStore())) {
            throw new ResourceNotFoundException(NotFoundMessages.orderByCode(order.getOrderCode()));
        }
    }

    public boolean sameStore(User actor, Store store) {
        return actor.getStore() != null && store != null && actor.getStore().getId().equals(store.getId());
    }
}
