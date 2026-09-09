package com.banhmyking.banhmyking.validator;

import com.banhmyking.banhmyking.entity.Order;
import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.enums.OrderStatus;
import com.banhmyking.banhmyking.enums.RoleName;
import com.banhmyking.banhmyking.exception.BusinessException;
import com.banhmyking.banhmyking.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusValidatorTest {

    private OrderStatusValidator validator;
    private User customer;
    private User otherCustomer;
    private User staff;
    private User admin;
    private User shipper;

    @BeforeEach
    void setUp() {
        validator = new OrderStatusValidator();

        customer = new User();
        customer.setId(2L);
        customer.setRole(RoleName.CUSTOMER);

        otherCustomer = new User();
        otherCustomer.setId(99L);
        otherCustomer.setRole(RoleName.CUSTOMER);

        staff = new User();
        staff.setId(4L);
        staff.setRole(RoleName.STAFF);

        admin = new User();
        admin.setId(1L);
        admin.setRole(RoleName.ADMIN);

        shipper = new User();
        shipper.setId(3L);
        shipper.setRole(RoleName.SHIPPER);
    }

    private Order createOrder(OrderStatus status, User owner) {
        Order order = new Order();
        order.setId(100L);
        order.setOrderCode("BMK-20260909-TEST1");
        order.setStatus(status);
        order.setUser(owner);
        return order;
    }

    @Nested
    @DisplayName("AC 1: State Machine & Transition Rules")
    class StateMachineTests {

        @Test
        @DisplayName("Chuyển trạng thái hợp lệ theo tuần tự (Staff/Admin)")
        void validTransitions_success() {
            Order order = createOrder(OrderStatus.PENDING, customer);
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.CONFIRMED, staff)).doesNotThrowAnyException();

            order.setStatus(OrderStatus.CONFIRMED);
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.PREPARING, staff)).doesNotThrowAnyException();

            order.setStatus(OrderStatus.PREPARING);
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.READY_FOR_PICKUP, staff)).doesNotThrowAnyException();

            order.setStatus(OrderStatus.READY_FOR_PICKUP);
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.DELIVERING, staff)).doesNotThrowAnyException();

            order.setStatus(OrderStatus.DELIVERING);
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.DELIVERED, staff)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC 1: FAILED chỉ được chuyển từ DELIVERING - Hợp lệ khi từ DELIVERING")
        void failed_fromDelivering_success() {
            Order order = createOrder(OrderStatus.DELIVERING, customer);
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.FAILED, staff)).doesNotThrowAnyException();
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.FAILED, shipper)).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP"})
        @DisplayName("AC 1: FAILED từ bất kỳ trạng thái nào trước DELIVERING đều bị chặn")
        void failed_fromNonDelivering_shouldThrowBusinessException(OrderStatus fromStatus) {
            Order order = createOrder(fromStatus, customer);

            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.FAILED, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Trạng thái FAILED chỉ được phép chuyển từ DELIVERING");
        }

        @Test
        @DisplayName("AC 1: Chặn nhảy cóc trạng thái (ví dụ PENDING -> DELIVERING)")
        void jumpingTransitions_shouldThrowBusinessException() {
            Order order = createOrder(OrderStatus.PENDING, customer);

            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.DELIVERING, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Không được phép nhảy cóc trạng thái");

            order.setStatus(OrderStatus.CONFIRMED);
            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.READY_FOR_PICKUP, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Không được phép nhảy cóc trạng thái");
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED", "FAILED"})
        @DisplayName("Chặn chuyển trạng thái khi đơn đã ở Terminal States")
        void terminalStates_shouldThrowBusinessException(OrderStatus terminalStatus) {
            Order order = createOrder(terminalStatus, customer);

            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.CONFIRMED, admin))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("đã kết thúc");
        }

        @Test
        @DisplayName("Chặn Customer tự ý cập nhật trạng thái tiến trình đơn hàng")
        void customer_cannotUpdateProcessStatus() {
            Order order = createOrder(OrderStatus.PENDING, customer);

            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.CONFIRMED, customer))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        @DisplayName("Shipper chỉ được cập nhật các trạng thái giao nhận (DELIVERING, DELIVERED, FAILED)")
        void shipper_operationalLimits() {
            Order order = createOrder(OrderStatus.PENDING, customer);

            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.CONFIRMED, shipper))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

            order.setStatus(OrderStatus.READY_FOR_PICKUP);
            assertThatCode(() -> validator.validateTransition(order, OrderStatus.DELIVERING, shipper)).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("AC 3: Phân quyền Hủy đơn hàng")
    class CancelOrderTests {

        @Test
        @DisplayName("Customer hủy đơn PENDING của chính mình thành công")
        void customer_cancelPending_success() {
            Order order = createOrder(OrderStatus.PENDING, customer);
            assertThatCode(() -> validator.validateCancel(order, customer, null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Customer hủy đơn CONFIRMED của chính mình thành công")
        void customer_cancelConfirmed_success() {
            Order order = createOrder(OrderStatus.CONFIRMED, customer);
            assertThatCode(() -> validator.validateCancel(order, customer, "Đổi ý")).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PREPARING", "READY_FOR_PICKUP", "DELIVERING"})
        @DisplayName("Customer bị chặn hủy khi đơn đã ở PREPARING trở đi")
        void customer_cancelPastConfirmed_shouldThrowBusinessException(OrderStatus status) {
            Order order = createOrder(status, customer);

            assertThatThrownBy(() -> validator.validateCancel(order, customer, "Muốn hủy"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Khách hàng chỉ có thể hủy đơn khi đơn hàng ở trạng thái PENDING hoặc CONFIRMED");
        }

        @Test
        @DisplayName("Customer bị chặn hủy đơn của người khác (Check ownership)")
        void customer_cancelOtherOrder_shouldThrowForbidden() {
            Order order = createOrder(OrderStatus.PENDING, customer);

            assertThatThrownBy(() -> validator.validateCancel(order, otherCustomer, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP"})
        @DisplayName("Staff/Admin được phép hủy tới bước READY_FOR_PICKUP khi có lý do")
        void staff_cancelUpToReadyForPickup_withReason_success(OrderStatus status) {
            Order order = createOrder(status, customer);
            assertThatCode(() -> validator.validateCancel(order, staff, "Hết nguyên liệu làm bánh")).doesNotThrowAnyException();
            assertThatCode(() -> validator.validateCancel(order, admin, "Sự cố kỹ thuật tại quán")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Staff/Admin bị chặn hủy nếu không cung cấp lý do hủy")
        void staff_cancelWithoutReason_shouldThrowValidationException() {
            Order order = createOrder(OrderStatus.PREPARING, customer);

            assertThatThrownBy(() -> validator.validateCancel(order, staff, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR))
                    .hasMessageContaining("bắt buộc phải cung cấp lý do");

            assertThatThrownBy(() -> validator.validateCancel(order, admin, "   "))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR))
                    .hasMessageContaining("bắt buộc phải cung cấp lý do");
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"DELIVERING", "DELIVERED", "FAILED"})
        @DisplayName("Staff/Admin bị chặn hủy khi đơn đã ở DELIVERING trở đi")
        void staff_cancelPastReadyForPickup_shouldThrowBusinessException(OrderStatus status) {
            Order order = createOrder(status, customer);

            assertThatThrownBy(() -> validator.validateCancel(order, staff, "Khách không nghe máy"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Nhân viên/Admin chỉ có thể hủy đơn tới bước READY_FOR_PICKUP");
        }

        @Test
        @DisplayName("Shipper không có quyền hủy đơn")
        void shipper_cancel_shouldThrowForbidden() {
            Order order = createOrder(OrderStatus.PREPARING, customer);

            assertThatThrownBy(() -> validator.validateCancel(order, shipper, "Không muốn giao"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        @DisplayName("Chặn hủy đơn đã bị hủy trước đó")
        void cancel_alreadyCancelledOrder_shouldThrowBusinessException() {
            Order order = createOrder(OrderStatus.CANCELLED, customer);

            assertThatThrownBy(() -> validator.validateCancel(order, admin, "Hủy lại"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Đơn hàng này đã bị hủy trước đó");
        }
    }
}
