package com.banhmyking.banhmyking.event;

/** Có phản hồi / hồ sơ ứng tuyển mới hoặc vừa đổi trạng thái xử lý. storeId null = toàn chuỗi. */
public record InboxChangedEvent(InboxType type, Long storeId) {
}
