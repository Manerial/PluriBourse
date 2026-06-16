package org.pluribourse.user;

import org.junit.jupiter.api.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.repositories.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.*;
import org.springframework.security.test.context.support.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.*;
import org.springframework.transaction.annotation.*;
import org.springframework.web.context.*;

import java.lang.annotation.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @WithSecurityContext(factory = WithAdminUserFactory.class)
    @interface WithAdminUser {
    }

    static class WithAdminUserFactory implements WithSecurityContextFactory<WithAdminUser> {
        @Override
        public SecurityContext createSecurityContext(WithAdminUser annotation) {
            var user = new User();
            user.setUsername("Admin");
            user.setPassword("encoded");
            user.setRole(Role.ADMIN);
            user.setPreferredLanguage(Language.FR);
            user.setForcePasswordChange(true);
            var details = new PluriBourseUserDetails(user);
            var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
            var ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            return ctx;
        }
    }

    @Test
    @WithAdminUser
    void me_returns_current_user_info() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("Admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.forcePasswordChange").value(true));
    }

    @Test
    void me_returns_401_when_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void change_password_with_valid_body_returns_200_and_clears_flag() throws Exception {
        // Use real DB entity so the controller gets the actual user ID for the update
        var adminUser = userRepository.findByUsername("Admin").orElseThrow();
        var details = new PluriBourseUserDetails(adminUser);

        mockMvc.perform(post("/api/auth/change-password")
                        .with(user(details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"newSecurePass1\"}"))
                .andExpect(status().isOk());

        var updatedUser = userRepository.findByUsername("Admin").orElseThrow();
        assertThat(updatedUser.isForcePasswordChange()).isFalse();
        assertThat(passwordEncoder.matches("newSecurePass1", updatedUser.getPassword())).isTrue();
    }

    @Test
    void change_password_with_too_short_password_returns_400() throws Exception {
        var adminUser = userRepository.findByUsername("Admin").orElseThrow();
        var details = new PluriBourseUserDetails(adminUser);

        mockMvc.perform(post("/api/auth/change-password")
                        .with(user(details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void change_password_without_complexity_returns_400() throws Exception {
        var adminUser = userRepository.findByUsername("Admin").orElseThrow();
        var details = new PluriBourseUserDetails(adminUser);

        mockMvc.perform(post("/api/auth/change-password")
                        .with(user(details))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"alllowercase1\"}"))
                .andExpect(status().isBadRequest());
    }
}
