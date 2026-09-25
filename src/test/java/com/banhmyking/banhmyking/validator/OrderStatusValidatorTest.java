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
import org.junit.jupiter.params.provider.CsvSource;
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
    @DisplayName("1. Input Validation Biên (Null Checks & Same Status)")
    class InputValidationTests {

        @Test
        @DisplayName("Order là null -> Ném NOT_FOUND")
        void validateTransition_nullOrder_shouldThrowNotFound() {
            assertThatThrownBy(() -> validator.validateTransition(null, OrderStatus.CONFIRMED, staff))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("Actor là null -> Ném UNAUTHORIZED")
        void validateTransition_nullActor_shouldThrowUnauthorized() {
            Order order = createOrder(OrderStatus.PENDING, customer);
            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.CONFIRMED, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @Test
        @DisplayName("NextStatus là null -> Ném VALIDATION_ERROR")
        void validateTransition_nullNextStatus_shouldThrowValidationError() {
            Order order = createOrder(OrderStatus.PENDING, customer);
            assertThatThrownBy(() -> validator.validateTransition(order, null, staff))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @ParameterizedTest
        @EnumSource(OrderStatus.class)
        @DisplayName("Chuyển sang chính trạng thái hiện tại (from == to) -> Ném BUSINESS_ERROR")
        void validateTransition_sameStatus_shouldThrowBusinessException(OrderStatus status) {
            Order order = createOrder(status, customer);
            assertThatThrownBy(() -> validator.validateTransition(order, status, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Đơn hàng hiện đã ở trạng thái " + status);
        }
    }

    @Nested
    @DisplayName("2. Ma trận 8x8 Chuyển đổi Trạng thái (Exhaustive State Machine Matrix)")
    class StateMachineMatrixTests {

        @ParameterizedTest
        @CsvSource({
                "PENDING, CONFIRMED",
                "CONFIRMED, PREPARING",
                "PREPARING, READY_FOR_PICKUP",
                "READY_FOR_PICKUP, DELIVERING",
                "DELIVERING, DELIVERED",
                "DELIVERING, FAILED"
        })
        @DisplayName("Tất cả các bước tiến trình hợp lệ theo tuần tự (Staff/Admin)")
        void validForwardTransitions_success(OrderStatus from, OrderStatus to) {
            Order order = createOrder(from, customer);
            assertThatCode(() -> validator.validateTransition(order, to, staff))
                    .doesNotThrowAnyException();
            assertThatCode(() -> validator.validateTransition(order, to, admin))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("AC 1: Vét cạn toàn bộ 64 tổ hợp (8x8) giữa các trạng thái")
        void exhaustiveAll64Combinations_matrixVerification() {
            OrderStatus[] allStatuses = OrderStatus.values();
            for (OrderStatus from : allStatuses) {
                for (OrderStatus to : allStatuses) {
                    Order order = createOrder(from, customer);

                    boolean shouldBeValid = from.canTransitionTo(to) && !(to == OrderStatus.FAILED && from != OrderStatus.DELIVERING);

                    if (shouldBeValid) {
                        assertThatCode(() -> validator.validateTransition(order, to, staff))
                                .as("Transition hợp lệ từ %s sang %s", from, to)
                                .doesNotThrowAnyException();
                    } else {
                        assertThatThrownBy(() -> validator.validateTransition(order, to, staff))
                                .as("Transition không hợp lệ từ %s sang %s phải bị chặn", from, to)
                                .isInstanceOf(BusinessException.class);
                    }
                }
            }
        }

        @ParameterizedTest
        @CsvSource({
                "PENDING, PREPARING",
                "PENDING, READY_FOR_PICKUP",
                "PENDING, DELIVERING",
                "PENDING, DELIVERED",
                "CONFIRMED, READY_FOR_PICKUP",
                "CONFIRMED, DELIVERING",
                "CONFIRMED, DELIVERED",
                "PREPARING, DELIVERING",
                "PREPARING, DELIVERED",
                "READY_FOR_PICKUP, DELIVERED"
        })
        @DisplayName("Chặn nhảy cóc trạng thái tiến về phía trước")
        void jumpingForwardTransitions_shouldThrowBusinessException(OrderStatus from, OrderStatus to) {
            Order order = createOrder(from, customer);
            assertThatThrownBy(() -> validator.validateTransition(order, to, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Không được phép nhảy cóc trạng thái");
        }

        @ParameterizedTest
        @CsvSource({
                "CONFIRMED, PENDING",
                "PREPARING, CONFIRMED",
                "READY_FOR_PICKUP, PREPARING",
                "DELIVERING, READY_FOR_PICKUP",
                "DELIVERING, PREPARING"
        })
        @DisplayName("Chặn quay ngược trạng thái về trước")
        void backwardTransitions_shouldThrowBusinessException(OrderStatus from, OrderStatus to) {
            Order order = createOrder(from, customer);
            assertThatThrownBy(() -> validator.validateTransition(order, to, staff))
                    .isInstanceOf(BusinessException.class);
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "CANCELLED", "FAILED"})
        @DisplayName("Terminal states (DELIVERED, CANCELLED, FAILED) không được phép chuyển sang bất kỳ trạng thái nào")
        void terminalStates_cannotTransitionToAnyState(OrderStatus terminal) {
            Order order = createOrder(terminal, customer);
            for (OrderStatus next : OrderStatus.values()) {
                assertThatThrownBy(() -> validator.validateTransition(order, next, admin))
                        .isInstanceOf(BusinessException.class);
            }
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP"})
        @DisplayName("FAILED từ bất kỳ trạng thái nào trước DELIVERING đều bị chặn tuyệt đối")
        void failed_fromNonDelivering_shouldThrowBusinessException(OrderStatus from) {
            Order order = createOrder(from, customer);
            assertThatThrownBy(() -> validator.validateTransition(order, OrderStatus.FAILED, staff))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Trạng thái FAILED chỉ được phép chuyển từ DELIVERING");
        }
    }

    @Nested
    @DisplayName("3. Phân quyền Vai trò khi Chuyển Trạng thái (Role-based Transitions)")
    class RoleBasedTransitionTests {

        @Test
        @DisplayName("Customer bị chặn (FORBIDDEN) khi cố duyệt đơn ở các bước tiến trình hợp lệ")
        void customer_cannotUpdateProcessStatus() {
            // PENDING -> CONFIRMED
            Order pendingOrder = createOrder(OrderStatus.PENDING, customer);
            assertThatThrownBy(() -> validator.validateTransition(pendingOrder, OrderStatus.CONFIRMED, customer))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

            // CONFIRMED -> PREPARING
            Order confirmedOrder = createOrder(OrderStatus.CONFIRMED, customer);
            assertThatThrownBy(() -> validator.validateTransition(confirmedOrder, OrderStatus.PREPARING, customer))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

            // Bất kỳ trạng thái nào Customer cũng bị chặn
            for (OrderStatus next : OrderStatus.values()) {
                Order order = createOrder(OrderStatus.PENDING, customer);
                assertThatThrownBy(() -> validator.validateTransition(order, next, customer))
                        .isInstanceOf(BusinessException.class);
            }
        }

        @Test
        @DisplayName("Shipper bị chặn (FORBIDDEN) khi cố cập nhật các trạng thái nội bộ của quán")
        void shipper_cannotUpdateKitchenStatuses() {
            // PENDING -> CONFIRMED
            Order pendingOrder = createOrder(OrderStatus.PENDING, customer);
            assertThatThrownBy(() -> validator.validateTransition(pendingOrder, OrderStatus.CONFIRMED, shipper))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

            // CONFIRMED -> PREPARING
            Order confirmedOrder = createOrder(OrderStatus.CONFIRMED, customer);
            assertThatThrownBy(() -> validator.validateTransition(confirmedOrder, OrderStatus.PREPARING, shipper))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));

            // PREPARING -> READY_FOR_PICKUP
            Order preparingOrder = createOrder(OrderStatus.PREPARING, customer);
            assertThatThrownBy(() -> validator.validateTransition(preparingOrder, OrderStatus.READY_FOR_PICKUP, shipper))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @Test
        @DisplayName("Shipper được phép thao tác các trạng thái giao nhận hợp lệ (DELIVERING, DELIVERED, FAILED)")
        void shipper_canUpdateDeliveryStatuses_whenValid() {
            // READY_FOR_PICKUP -> DELIVERING
            Order pickupOrder = createOrder(OrderStatus.READY_FOR_PICKUP, customer);
            assertThatCode(() -> validator.validateTransition(pickupOrder, OrderStatus.DELIVERING, shipper))
                    .doesNotThrowAnyException();

            // DELIVERING -> DELIVERED
            Order deliveringOrder1 = createOrder(OrderStatus.DELIVERING, customer);
            assertThatCode(() -> validator.validateTransition(deliveringOrder1, OrderStatus.DELIVERED, shipper))
                    .doesNotThrowAnyException();

            // DELIVERING -> FAILED
            Order deliveringOrder2 = createOrder(OrderStatus.DELIVERING, customer);
            assertThatCode(() -> validator.validateTransition(deliveringOrder2, OrderStatus.FAILED, shipper))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("4. Phân quyền và Ràng buộc Hủy đơn hàng (Cancel Order Rules)")
    class CancelOrderTests {

        @Test
        @DisplayName("validateCancel với Order null -> Ném NOT_FOUND")
        void validateCancel_nullOrder_shouldThrowNotFound() {
            assertThatThrownBy(() -> validator.validateCancel(null, customer, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("validateCancel với Actor null -> Ném UNAUTHORIZED")
        void validateCancel_nullActor_shouldThrowUnauthorized() {
            Order order = createOrder(OrderStatus.PENDING, customer);
            assertThatThrownBy(() -> validator.validateCancel(order, null, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }

        @Test
        @DisplayName("Chặn hủy đơn đã bị hủy trước đó")
        void cancel_alreadyCancelledOrder_shouldThrowBusinessException() {
            Order order = createOrder(OrderStatus.CANCELLED, customer);
            assertThatThrownBy(() -> validator.validateCancel(order, admin, "Hủy lại"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Đơn hàng này đã bị hủy trước đó");
        }

        @Test
        @DisplayName("Customer hủy đơn PENDING / CONFIRMED của chính mình thành công")
        void customer_cancelPendingAndConfirmed_success() {
            Order pendingOrder = createOrder(OrderStatus.PENDING, customer);
            assertThatCode(() -> validator.validateCancel(pendingOrder, customer, null)).doesNotThrowAnyException();

            Order confirmedOrder = createOrder(OrderStatus.CONFIRMED, customer);
            assertThatCode(() -> validator.validateCancel(confirmedOrder, customer, "Đổi ý")).doesNotThrowAnyException();
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PREPARING", "READY_FOR_PICKUP", "DELIVERING", "DELIVERED", "FAILED"})
        @DisplayName("Customer bị chặn hủy khi đơn đã ở PREPARING trở đi")
        void customer_cancelPastConfirmed_shouldThrowBusinessException(OrderStatus status) {
            Order order = createOrder(status, customer);
            assertThatThrownBy(() -> validator.validateCancel(order, customer, "Muốn hủy"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Khách hàng chỉ có thể hủy đơn khi đơn hàng ở trạng thái PENDING hoặc CONFIRMED");
        }

        @Test
        @DisplayName("Customer bị chặn hủy đơn của người khác (Ownership Check)")
        void customer_cancelOtherCustomerOrder_shouldThrowForbidden() {
            Order order = createOrder(OrderStatus.PENDING, customer);
            assertThatThrownBy(() -> validator.validateCancel(order, otherCustomer, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN))
                    .hasMessageContaining("Bạn không có quyền hủy đơn hàng của người khác");
        }

        @Test
        @DisplayName("Customer bị chặn hủy đơn khi order.getUser() bị null")
        void customer_cancelOrderWithNullUser_shouldThrowForbidden() {
            Order order = createOrder(OrderStatus.PENDING, null);
            assertThatThrownBy(() -> validator.validateCancel(order, customer, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP"})
        @DisplayName("Staff/Admin được phép hủy tới bước READY_FOR_PICKUP khi có lý do")
        void staffAndAdmin_cancelUpToReadyForPickup_withReason_success(OrderStatus status) {
            Order order = createOrder(status, customer);
            assertThatCode(() -> validator.validateCancel(order, staff, "Hết nguyên liệu")).doesNotThrowAnyException();
            assertThatCode(() -> validator.validateCancel(order, admin, "Quán đóng cửa sớm")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Staff/Admin bị chặn hủy nếu không cung cấp lý do hủy (null hoặc rỗng)")
        void staffAndAdmin_cancelWithoutReason_shouldThrowValidationException() {
            Order order = createOrder(OrderStatus.PREPARING, customer);

            assertThatThrownBy(() -> validator.validateCancel(order, staff, null))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

            assertThatThrownBy(() -> validator.validateCancel(order, admin, "   "))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"DELIVERING", "DELIVERED", "FAILED"})
        @DisplayName("Staff/Admin bị chặn hủy khi đơn đã ở DELIVERING trở đi")
        void staffAndAdmin_cancelPastReadyForPickup_shouldThrowBusinessException(OrderStatus status) {
            Order order = createOrder(status, customer);
            assertThatThrownBy(() -> validator.validateCancel(order, staff, "Khách không nhận"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Nhân viên/Admin chỉ có thể hủy đơn tới bước READY_FOR_PICKUP");
        }

        @Test
        @DisplayName("Shipper không có quyền hủy bất kỳ đơn hàng nào")
        void shipper_cancel_shouldThrowForbidden() {
            Order order = createOrder(OrderStatus.PREPARING, customer);
            assertThatThrownBy(() -> validator.validateCancel(order, shipper, "Không nhận giao"))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        }
    }
}
