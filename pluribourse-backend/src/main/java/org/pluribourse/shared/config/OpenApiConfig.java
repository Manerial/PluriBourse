package org.pluribourse.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes Springdoc OpenAPI UI only when {@code springdoc.api-docs.enabled=true} (dev profile).
 * In prod, the property is false → this bean is not registered → /swagger-ui.html returns 404.
 */
@Configuration
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class OpenApiConfig {

    @Bean
    public OpenAPI pluriboursOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PluriBourse API")
                        .description("API for managing secondhand sale events")
                        .version("1.0.0"));
    }
}
