package org.pluribourse.shared.sse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseEmitterRegistry {

    /**
     * Package-private so {@link SseEmitterRegistryKeepaliveTest} can register test doubles directly —
     * {@link #register()} builds a real {@link SseEmitter} that cannot be driven outside a live async
     * request, and the keepalive path has no other seam.
     */
    final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Defaulted to {@code true} so a non-Spring {@code new SseEmitterRegistry()} (the isolated
     * keepalive test) keeps the production behaviour; Spring overrides it from
     * {@code sse.keepalive.enabled}. The test profile sets that property to {@code false} so the
     * scheduled comment never fires during the ordered story-board ITs. Package-private (like
     * {@code emitters}) so {@link SseEmitterRegistryKeepaliveTest} can toggle it without reflection.
     */
    @Value("${sse.keepalive.enabled:true}")
    boolean keepaliveEnabled = true;

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        synchronized (this) {
            emitters.add(emitter);
            emitter.onCompletion(() -> emitters.remove(emitter));
            emitter.onTimeout(() -> emitters.remove(emitter));
            emitter.onError(e -> emitters.remove(emitter));
        }
        // Initial frame: commits the 200 text/event-stream response right away so the browser's
        // EventSource reaches OPEN without waiting for a business event. Without it a bare emitter
        // writes no bytes until the first broadcast, and an idle reverse-proxy (nginx
        // proxy_read_timeout, default 60 s) drops the connection with a synthetic 504 before the
        // headers ever leave the backend. Sent outside the lock: this write can drain slowly on a
        // half-open client and must not block other subscribers. Same defensive removal as
        // broadcast() for a client that disconnected between the request and this line.
        try {
            emitter.send(SseEmitter.event().comment("ok"));
        } catch (IOException | RuntimeException e) {
            emitters.remove(emitter);
            emitter.completeWithError(e);
        }
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
                emitter.completeWithError(e);
            }
        }
    }

    /**
     * Writes a bare SSE comment ({@code :keepalive}) to every open connection on a fixed delay so an
     * intermediary (reverse-proxy, load balancer) never sees an idle SSE socket as dead and cuts it —
     * which the client would then mistake for a logout. The comment carries no data, does not touch
     * {@code SPRING_SESSION}, and is invisible to the browser's {@code EventSource} message handlers.
     * <p>
     * This is independent of the {@link SseEmitter} constructor timeout: the keepalive defeats an
     * <em>idle</em> proxy timeout, not the emitter's absolute request lifetime, which still ends each
     * connection after 30 min (the browser then reconnects natively).
     * <p>
     * Same defensive removal as {@link #broadcast(String, Object)}: an emitter whose send fails is
     * dropped from {@code emitters} and completed with the error. Guarded by
     * {@code sse.keepalive.enabled} (checked here, not via {@code @ConditionalOnProperty}, because
     * {@code SseEmitterRegistry} is a core bean that must always exist).
     */
    @Scheduled(fixedDelayString = "${sse.keepalive.interval:PT20S}", initialDelayString = "${sse.keepalive.interval:PT20S}")
    public void sendKeepalive() {
        if (!keepaliveEnabled) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException | RuntimeException e) {
                emitters.remove(emitter);
                emitter.completeWithError(e);
            }
        }
    }
}
