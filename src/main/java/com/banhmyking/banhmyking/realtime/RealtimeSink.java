package com.banhmyking.banhmyking.realtime;

import java.io.IOException;

/** Đầu ra của một kết nối. Tách khỏi SseEmitter để test hub không cần Spring. */
public interface RealtimeSink {

    void send(String eventName, Object data) throws IOException;

    void sendComment(String comment) throws IOException;

    void close();
}
