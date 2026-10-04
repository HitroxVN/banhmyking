package com.banhmyking.banhmyking.controller;

import com.banhmyking.banhmyking.entity.User;
import com.banhmyking.banhmyking.exception.ResourceNotFoundException;
import com.banhmyking.banhmyking.realtime.RealtimeHub;
import com.banhmyking.banhmyking.realtime.RealtimeUser;
import com.banhmyking.banhmyking.realtime.SseEmitterSink;
import com.banhmyking.banhmyking.repository.UserRepository;
import com.banhmyking.banhmyking.security.SecurityUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;

/** Luồng tín hiệu realtime (spec realtime §4). Mọi vai trò đã đăng nhập đều mở được. */
@RestController
@RequestMapping("/api/v1/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final RealtimeHub hub;
    private final UserRepository userRepository;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal UserDetails principal, HttpServletResponse response) {
        Long userId = SecurityUtils.requireUserId(principal);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại với ID: " + userId));

        // Không cho proxy (Nginx) gom dữ liệu — tin phải tới ngay
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String connectionId = hub.register(RealtimeUser.from(user), new SseEmitterSink(emitter));
        emitter.onCompletion(() -> hub.unregister(connectionId));
        emitter.onTimeout(() -> {
            hub.unregister(connectionId);
            emitter.complete();
        });
        emitter.onError(ex -> hub.unregister(connectionId));
        return emitter;
    }
}
