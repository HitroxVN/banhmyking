package com.banhmyking.banhmyking.realtime;

import com.banhmyking.banhmyking.event.InboxChangedEvent;
import com.banhmyking.banhmyking.event.OrderChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Giữ các kết nối SSE đang mở (trong bộ nhớ — chỉ đúng với một instance backend, spec §8)
 * và gửi tín hiệu tới đúng người. Lỗi gửi chỉ làm rơi kết nối đó, không bao giờ ném ra ngoài.
 */
@Slf4j
@Component
public class RealtimeHub {

    static final int MAX_CONNECTIONS_PER_USER = 5;

    private record Connection(String id, RealtimeUser user, RealtimeSink sink, long sequence) {
    }

    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public String register(RealtimeUser user, RealtimeSink sink) {
        String id = UUID.randomUUID().toString();
        Connection connection = new Connection(id, user, sink, sequence.incrementAndGet());
        connections.put(id, connection);
        evictOverflow(user);
        deliver(connection, "ready", Map.of("connectionId", id));
        return id;
    }

    public void unregister(String connectionId) {
        connections.remove(connectionId);
    }

    public void publish(OrderChangedEvent event) {
        OrderSignal signal = OrderSignal.from(event);
        for (Connection connection : connections.values()) {
            if (RealtimeRouting.receives(connection.user(), event)) {
                deliver(connection, "order", signal);
            }
        }
    }

    public void publish(InboxChangedEvent event) {
        InboxSignal signal = new InboxSignal(event.type());
        for (Connection connection : connections.values()) {
            if (RealtimeRouting.receives(connection.user(), event)) {
                deliver(connection, "inbox", signal);
            }
        }
    }

    /** Giữ kết nối qua proxy và phát hiện trình duyệt đã đóng mà server chưa biết. */
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        for (Connection connection : connections.values()) {
            try {
                connection.sink().sendComment("ping");
            } catch (Exception ex) {
                drop(connection, ex);
            }
        }
    }

    public int connectionCount() {
        return connections.size();
    }

    private void deliver(Connection connection, String eventName, Object data) {
        try {
            connection.sink().send(eventName, data);
        } catch (Exception ex) {
            drop(connection, ex);
        }
    }

    private void drop(Connection connection, Exception cause) {
        log.debug("Realtime connection {} of user {} dropped: {}", connection.id(),
                connection.user().userId(), cause.toString());
        connections.remove(connection.id());
        closeQuietly(connection.sink());
    }

    /** Quá giới hạn thì đóng kết nối CŨ NHẤT — tab cũ tự chuyển polling rồi nối lại. */
    private void evictOverflow(RealtimeUser user) {
        List<Connection> mine = connections.values().stream()
                .filter(c -> c.user().userId() != null && c.user().userId().equals(user.userId()))
                .sorted(Comparator.comparingLong(Connection::sequence))
                .toList();
        for (int i = 0; i < mine.size() - MAX_CONNECTIONS_PER_USER; i++) {
            Connection oldest = mine.get(i);
            connections.remove(oldest.id());
            closeQuietly(oldest.sink());
        }
    }

    private static void closeQuietly(RealtimeSink sink) {
        try {
            sink.close();
        } catch (Exception ignored) {
            // Kết nối đã hỏng sẵn — không còn gì để đóng
        }
    }
}
