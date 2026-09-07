package org.pluribourse.shared.sse;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Isolated (no Spring context) coverage of {@link SseEmitterRegistry#sendKeepalive()}. The outbound
 * SSE stream is a boundary with the outside world — a live HTTP connection held open by the browser —
 * of the same order as the external-system clients CLAUDE.md exempts from the E2E-only rule. This
 * complements, and does not replace, the end-to-end SSE coverage in {@code PhaseTransitionIT} /
 * {@code PosBasketCancellationIT} / {@code SettlementSyncIT}.
 * <p>
 * No Mockito: {@link SseEmitterRegistry} is core infrastructure, not an external-system client in the
 * {@code PrinterBridgeClient} sense. The test drives real {@link SseEmitter} subclasses registered
 * straight into the package-private {@code emitters} list.
 */
class SseEmitterRegistryKeepaliveTest {

    /** Captures every builder handed to {@link #send}, and can be told to fail its next send. */
    private static class RecordingEmitter extends SseEmitter {

        private final List<String> sent = new ArrayList<>();
        private boolean failNextSend;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failNextSend) {
                throw new IOException("client gone");
            }
            StringBuilder captured = new StringBuilder();
            builder.build().forEach(entry -> captured.append(entry.getData()));
            sent.add(captured.toString());
        }
    }

    @Test
    void sends_a_keepalive_comment_to_every_open_emitter() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        RecordingEmitter first = new RecordingEmitter();
        RecordingEmitter second = new RecordingEmitter();
        registry.emitters.add(first);
        registry.emitters.add(second);

        registry.sendKeepalive();

        assertThat(first.sent).singleElement().asString().contains("keepalive");
        assertThat(second.sent).singleElement().asString().contains("keepalive");
        assertThat(registry.emitters).containsExactly(first, second);
    }

    @Test
    void drops_an_emitter_whose_send_fails() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        RecordingEmitter healthy = new RecordingEmitter();
        RecordingEmitter broken = new RecordingEmitter();
        broken.failNextSend = true;
        registry.emitters.add(healthy);
        registry.emitters.add(broken);

        registry.sendKeepalive();

        assertThat(registry.emitters).containsExactly(healthy);
        assertThat(healthy.sent).singleElement().asString().contains("keepalive");
    }

    @Test
    void does_nothing_when_the_keepalive_is_disabled() {
        SseEmitterRegistry registry = new SseEmitterRegistry();
        registry.keepaliveEnabled = false;
        RecordingEmitter emitter = new RecordingEmitter();
        registry.emitters.add(emitter);

        registry.sendKeepalive();

        assertThat(emitter.sent).isEmpty();
        assertThat(registry.emitters).containsExactly(emitter);
    }
}
