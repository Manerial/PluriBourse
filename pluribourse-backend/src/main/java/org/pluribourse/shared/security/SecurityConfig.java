package org.pluribourse.shared.security;

import com.fasterxml.jackson.databind.*;
import org.pluribourse.shared.security.handlers.*;
import org.pluribourse.user.services.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.authorization.*;
import org.springframework.security.config.annotation.method.configuration.*;
import org.springframework.security.config.annotation.web.builders.*;
import org.springframework.security.config.annotation.web.configuration.*;
import org.springframework.security.crypto.bcrypt.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.*;
import org.springframework.security.web.csrf.*;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public ForcePasswordChangeFilter forcePasswordChangeFilter(ObjectMapper objectMapper) {
        return new ForcePasswordChangeFilter(objectMapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           PluriBourseUserDetailsService uds,
                                           PasswordEncoder pe,
                                           LoginSuccessHandler loginSuccessHandler,
                                           LoginFailureHandler loginFailureHandler,
                                           LogoutSuccessHandler logoutSuccessHandler,
                                           ForcePasswordChangeFilter forcePasswordChangeFilter) {

        // Build the provider inline — not a @Bean — so Spring Security's
        // global AuthenticationManager does not register a duplicate via
        // AuthenticationProviderBeanManagerConfigurer.
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(uds);
        authProvider.setPasswordEncoder(pe);

        http
                .authenticationProvider(authProvider)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/auth/login")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint())
                        .accessDeniedHandler(new ProblemDetailAccessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/auth/login").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Require authenticated non-anonymous user AND block SELLER role
                        .anyRequest().access((authentication, context) -> {
                            Authentication a = authentication.get();
                            boolean isAuthenticated = a.isAuthenticated()
                                    && !(a instanceof AnonymousAuthenticationToken);
                            boolean notSeller = a.getAuthorities().stream()
                                    .noneMatch(ga -> ga.getAuthority().equals("ROLE_SELLER"));
                            return new AuthorizationDecision(isAuthenticated && notSeller);
                        })
                )
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(logoutSuccessHandler)
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "SESSION")
                )
                .addFilterAfter(forcePasswordChangeFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
