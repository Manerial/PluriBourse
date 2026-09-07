package org.pluribourse.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's {@code @Scheduled} support — the project's first use of it (story 4.9,
 * {@link org.pluribourse.domain.pos.service.BasketReaperService}). Kept on a dedicated
 * {@code @Configuration} rather than on {@code PluribourseApplication} because that class also runs
 * as a CLI ({@code WebApplicationType.NONE}), where a scheduler has no place. This
 * {@code @Configuration} is still component-scanned by every {@code @SpringBootTest}, so it is not
 * what keeps the test suite quiet: with {@code pos.basket.reaper.enabled=false} the reaper bean is
 * absent (its {@code @ConditionalOnProperty}), there is no other {@code @Scheduled} method, and
 * Spring starts no scheduler thread. {@code spring-context} (transitive via the starters) provides
 * the scheduler — no {@code pom.xml} dependency, no Quartz.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
