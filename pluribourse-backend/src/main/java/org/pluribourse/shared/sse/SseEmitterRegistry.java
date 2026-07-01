package org.pluribourse.shared.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEmitterRegistry {

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public synchronized SseEmitter register() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /**
     * Broadcasts an SSE event to all registered emitters, keeping each connection open so it
     * can receive subsequent events. Completing the emitter after every send would force the
     * client to reconnect, and any event broadcast during that reconnect window would be lost —
     * emitters are only removed when the client actually disconnects (onCompletion/onTimeout/onError).
     */
    public void broadcast(String eventName, Object payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | RuntimeException e) {
                emitters.remove(emitter);
            }
        }
    }
}
