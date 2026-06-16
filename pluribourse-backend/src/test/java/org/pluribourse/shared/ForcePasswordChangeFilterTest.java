package org.pluribourse.shared;

import org.junit.jupiter.api.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.context.*;
import org.springframework.security.test.context.support.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.*;
import org.springframework.web.context.*;

import java.lang.annotation.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ForcePasswordChangeFilterTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @WithSecurityContext(factory = WithForcePasswordChangeUserFactory.class)
    @interface WithForcePasswordChangeUser {
    }

    static class WithForcePasswordChangeUserFactory
            implements WithSecurityContextFactory<WithForcePasswordChangeUser> {
        @Override
        public SecurityContext createSecurityContext(WithForcePasswordChangeUser annotation) {
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
    @WithForcePasswordChangeUser
    void api_auth_me_is_exempt_from_force_password_change_filter() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk());
    }

    @Test
    @WithForcePasswordChangeUser
    void non_exempt_endpoint_returns_403_with_problem_detail() throws Exception {
        mockMvc.perform(get("/api/sellers"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(result ->
                        assertThat(result.getResponse().getContentAsString())
                                .contains("password-change-required"));
    }

    @Test
    @WithForcePasswordChangeUser
    void change_password_endpoint_is_exempt_not_blocked_by_filter() throws Exception {
        // Filter does not intercept /api/auth/change-password — Spring Security or controller responds
        // Invalid body → 400, but NOT 403 from our ForcePasswordChangeFilter
        mockMvc.perform(post("/api/auth/change-password")
                        .with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(result ->
                        assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }
}
