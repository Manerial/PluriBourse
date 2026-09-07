package org.pluribourse.shared.sse;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEmitterRegistry registry;

    @GetMapping("/events")
    public SseEmitter subscribe(HttpServletResponse response) {
        // Tells nginx (and any proxy that honours it) not to buffer this response, so keepalive
        // comments and events reach the client immediately rather than being held back.
        response.setHeader("X-Accel-Buffering", "no");
        return registry.register();
    }
}
