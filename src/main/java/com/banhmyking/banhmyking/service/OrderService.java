package com.banhmyking.banhmyking.service;

import com.banhmyking.banhmyking.dto.common.PageResponse;
import com.banhmyking.banhmyking.dto.order.AssignShipperRequest;
import com.banhmyking.banhmyking.dto.order.CancelOrderRequest;
import com.banhmyking.banhmyking.dto.order.ConfirmDeliveryRequest;
import com.banhmyking.banhmyking.dto.order.CreateOrderRequest;
import com.banhmyking.banhmyking.dto.order.OrderResponse;
import com.banhmyking.banhmyking.dto.order.OrderStatusHistoryResponse;
import com.banhmyking.banhmyking.dto.order.RejectOrderRequest;
import com.banhmyking.banhmyking.dto.order.UpdateOrderStatusRequest;
import com.banhmyking.banhmyking.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    /**
     * Tạo đơn hàng từ giỏ hàng hiện tại của user.
     * Toàn bộ thao tác thực hiện trong 1 transaction.
     * Snapshot chính xác tên, giá món, topping, địa chỉ và cột tiền.
     * Tính tiền qua PriceCalculator.
     * Tự động xóa sạch giỏ hàng sau khi tạo đơn thành công.
     */
    OrderResponse createFromCart(Long userId, CreateOrderRequest request);

    /**
     * Lấy chi tiết đơn hàng theo mã orderCode.
     */
    OrderResponse getOrderByCode(Long userId, String orderCode);

    /**
     * Lấy danh sách các đơn hàng của user (không phân trang - backward compatible).
     */
    List<OrderResponse> getUserOrders(Long userId);

    /**
     * Lấy danh sách các đơn hàng của user có phân trang (Customer).
     */
    PageResponse<OrderResponse> getUserOrders(Long userId, int page, int size);

    /**
     * Lấy toàn bộ đơn hàng hệ thống kèm bộ lọc status, fromDate, toDate và phân trang (Staff/Admin).
     */
    PageResponse<OrderResponse> getAllOrdersForAdmin(Long userId, OrderStatus status, String fromDate, String toDate, int page, int size);

    /**
     * Gán Shipper phụ trách giao đơn hàng (Staff/Admin).
     */
    OrderResponse assignShipper(Long userId, String orderCode, AssignShipperRequest request);

    /**
     * Lấy danh sách các đơn hàng được gán cho Shipper kèm phân trang và lọc trạng thái.
     */
    PageResponse<OrderResponse> getOrdersForShipper(Long userId, OrderStatus status, int page, int size);

    /**
     * Xác nhận giao hàng thành công (Shipper). Chuyển DELIVERING -> DELIVERED.
     */
    OrderResponse confirmDelivery(Long userId, String orderCode, ConfirmDeliveryRequest request);

    /**
     * Shipper từ chối nhận đơn hàng được gán (chỉ khi đơn ở READY_FOR_PICKUP).
     * Gỡ gán shipper (shipper = null) và ghi nhận lý do vào lịch sử trạng thái.
     */
    OrderResponse rejectAssignedOrder(Long userId, String orderCode, RejectOrderRequest request);

    /**
     * Hủy đơn hàng tuân thủ phân quyền (AC 3):
     * - Customer: PENDING hoặc CONFIRMED, có check ownership.
     * - Staff/Admin: Tới bước READY_FOR_PICKUP, bắt buộc phải kèm lý do hủy.
     * Tự động ghi 1 dòng vào order_status_history (AC 2).
     */
    OrderResponse cancelOrder(Long userId, String orderCode, CancelOrderRequest request);

    /**
     * Cập nhật trạng thái đơn hàng (AC 1):
     * - Kiểm tra bằng OrderStatusValidator, chặn nhảy trạng thái.
     * - FAILED chỉ từ DELIVERING.
     * Tự động ghi 1 dòng vào order_status_history (AC 2).
     */
    OrderResponse updateOrderStatus(Long userId, String orderCode, UpdateOrderStatusRequest request);

    /**
     * Lấy lịch sử các lần chuyển trạng thái của đơn hàng.
     */
    List<OrderStatusHistoryResponse> getOrderStatusHistory(Long userId, String orderCode);

    /**
     * Lấy danh sách tài xế (Shipper) kèm số lượng đơn đang giao để phục vụ điều phối (Staff/Admin).
     */
    List<com.banhmyking.banhmyking.dto.order.ShipperAvailabilityResponse> getAvailableShippers(Long userId);
}
