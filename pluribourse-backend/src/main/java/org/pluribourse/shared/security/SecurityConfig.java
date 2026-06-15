package org.pluribourse.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security config for Story 1.1: allows /actuator/health, blocks everything else.
 * Form login, Spring Session JDBC, roles and CSRF are wired in Story 1.2.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            // CSRF disabled until Story 1.2 configures form login + session management
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
