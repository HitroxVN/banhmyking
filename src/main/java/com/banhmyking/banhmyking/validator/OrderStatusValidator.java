package com.banhmyking.banhmyking.validator;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * Validator quản lý chuyển trạng thái đơn hàng (State Machine) và tính năng hủy đơn.
 */
@Component
public class OrderStatusValidator {

    /**
     * AC 1: Validate chuyển trạng thái hợp lệ.
     * - Chặn nhảy trạng thái.
     * - FAILED chỉ được chuyển từ DELIVERING.
     * - Chặn khi đơn đã ở terminal states.
     * - Phân quyền theo vai trò người thực hiện (Actor).
     */
    public void validateTransition(Order order, OrderStatus nextStatus, User actor) {
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại");
        }
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Người thực hiện không hợp lệ");
        }
        if (nextStatus == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái mới không được để trống");
        }

        OrderStatus currentStatus = order.getStatus();

        // Không cho chuyển sang cùng trạng thái hiện tại
        if (currentStatus == nextStatus) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Đơn hàng hiện đã ở trạng thái " + currentStatus);
        }

        // Chặn chuyển tiếp nếu đơn đã ở trạng thái kết thúc (terminal)
        if (isTerminalState(currentStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Đơn hàng đã kết thúc ở trạng thái " + currentStatus + ", không thể chuyển tiếp");
        }

        // Quy định nghiêm ngặt: FAILED chỉ được chuyển từ DELIVERING (AC 1)
        if (nextStatus == OrderStatus.FAILED && currentStatus != OrderStatus.DELIVERING) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Trạng thái FAILED chỉ được phép chuyển từ DELIVERING. Trạng thái hiện tại: " + currentStatus);
        }

        // Chặn nhảy trạng thái thông qua canTransitionTo
        if (!currentStatus.canTransitionTo(nextStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Không thể chuyển trạng thái từ " + currentStatus + " sang " + nextStatus + ". Không được phép nhảy cóc trạng thái");
        }

        // Kiểm tra quyền thao tác đổi trạng thái tiến trình
        RoleName role = actor.getRole();
        if (role == RoleName.CUSTOMER) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Khách hàng không có quyền cập nhật trạng thái tiến trình đơn hàng");
        }

        if (role == RoleName.SHIPPER) {
            // Shipper chỉ được cập nhật các bước giao nhận
            if (nextStatus != OrderStatus.DELIVERING && nextStatus != OrderStatus.DELIVERED && nextStatus != OrderStatus.FAILED) {
                throw new BusinessException(ErrorCode.FORBIDDEN,
                        "Shipper chỉ có quyền cập nhật các trạng thái giao nhận (DELIVERING, DELIVERED, FAILED)");
            }
        }
    }

    /**
     * AC 3: Xử lý Hủy đơn tuân thủ phân quyền.
     * - Customer: Chỉ cho phép hủy khi PENDING hoặc CONFIRMED, có check ownership.
     * - Staff/Admin: Cho phép hủy tới bước READY_FOR_PICKUP, bắt buộc phải kèm lý do hủy.
     */
    public void validateCancel(Order order, User actor, String cancelReason) {
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Đơn hàng không tồn tại");
        }
        if (actor == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Người thực hiện không hợp lệ");
        }

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == OrderStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Đơn hàng này đã bị hủy trước đó");
        }

        RoleName role = actor.getRole();

        if (role == RoleName.CUSTOMER) {
            // Check ownership
            if (order.getUser() == null || !order.getUser().getId().equals(actor.getId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy đơn hàng của người khác");
            }

            // Chỉ cho phép hủy khi PENDING hoặc CONFIRMED
            if (!currentStatus.cancelableByCustomer()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Khách hàng chỉ có thể hủy đơn khi đơn hàng ở trạng thái PENDING hoặc CONFIRMED. Trạng thái hiện tại: " + currentStatus);
            }
        } else if (role == RoleName.STAFF || role == RoleName.ADMIN) {
            // Cho phép hủy tới bước READY_FOR_PICKUP (PENDING, CONFIRMED, PREPARING, READY_FOR_PICKUP)
            if (!currentStatus.cancelableByStaff()) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Nhân viên/Admin chỉ có thể hủy đơn tới bước READY_FOR_PICKUP. Trạng thái hiện tại: " + currentStatus);
            }

            // Bắt buộc phải kèm lý do hủy
            if (cancelReason == null || cancelReason.trim().isEmpty()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Nhân viên/Admin bắt buộc phải cung cấp lý do khi hủy đơn");
            }
        } else {
            // Shipper hoặc vai trò khác không được phép hủy
            throw new BusinessException(ErrorCode.FORBIDDEN, "Shipper không có quyền thực hiện hủy đơn hàng");
        }
    }

    private boolean isTerminalState(OrderStatus status) {
        return status == OrderStatus.DELIVERED
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.FAILED;
    }
}
