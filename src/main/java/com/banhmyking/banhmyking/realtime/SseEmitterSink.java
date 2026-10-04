package com.banhmyking.banhmyking.realtime;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/** RealtimeSink thật: ghi tin SSE (data JSON) ra SseEmitter. */
public class SseEmitterSink implements RealtimeSink {

    private final SseEmitter emitter;

    public SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data, MediaType.APPLICATION_JSON));
    }

    @Override
    public void sendComment(String comment) throws IOException {
        emitter.send(SseEmitter.event().comment(comment));
    }

    @Override
    public void close() {
        emitter.complete();
    }
}
