package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.event.InboxType;

/** Payload tin "inbox" gửi xuống trình duyệt. */
public record InboxSignal(InboxType type) {
}
