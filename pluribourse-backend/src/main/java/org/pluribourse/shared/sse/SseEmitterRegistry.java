package org.pluribourse.shared.sse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
     * Broadcasts an SSE event to all registered emitters, then closes each one.
     * Clients (Angular EventSource) reconnect automatically per RFC 8895.
     * Snapshot and clear are synchronized to prevent double-delivery under concurrent calls.
     */
    public void broadcast(String eventName, Object payload) {
        List<SseEmitter> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(emitters);
            emitters.clear();
        }
        for (SseEmitter emitter : snapshot) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
                emitter.complete();
            } catch (IOException | RuntimeException e) {
                // emitter was already dead or completed — ignore
            }
        }
    }
}
