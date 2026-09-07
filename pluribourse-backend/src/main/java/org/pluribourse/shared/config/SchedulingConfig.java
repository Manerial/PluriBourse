package org.pluribourse.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support — first introduced in story 4.9 for
 * {@link org.pluribourse.domain.pos.service.BasketReaperService}. Kept on a dedicated
 * {@code @Configuration} rather than on {@code PluribourseApplication} because that class also runs
 * as a CLI ({@code WebApplicationType.NONE}), where a scheduler has no place.
 * <p>
 * Two {@code @Scheduled} methods now exist, guarded differently:
 * <ul>
 *   <li>{@link org.pluribourse.domain.pos.service.BasketReaperService#reapInactiveBaskets()} —
 *       guarded by a class-level {@code @ConditionalOnProperty("pos.basket.reaper.enabled")}, so with
 *       the property {@code false} (the test profile) the bean, and its scheduled trigger, do not
 *       exist at all.</li>
 *   <li>{@link org.pluribourse.shared.sse.SseEmitterRegistry#sendKeepalive()} — no
 *       {@code @ConditionalOnProperty}: {@code SseEmitterRegistry} is a core bean that must always
 *       exist, so the {@code sse.keepalive.enabled} guard is inside the method. With the property
 *       {@code false} (the test profile) the scheduled trigger is still registered and fires, but
 *       the method returns immediately without touching any emitter.</li>
 * </ul>
 * As a result a scheduler thread does run under {@code @SpringBootTest} (for the keepalive trigger),
 * but it does no observable work while {@code sse.keepalive.enabled=false}.
 * {@code spring-context} (transitive via the starters) provides the scheduler — no {@code pom.xml}
 * dependency, no Quartz.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
