package org.pluribourse.user;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.pluribourse.shared.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.*;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VolunteerSessionRevocationIT extends IntegrationTest {

    // Spring Session's default cookie name (CookieHttpSessionIdResolver). Unlike the plain
    // MockHttpSession reuse pattern used by UserManagementIT, this class needs requests to go
    // through the real springSessionRepositoryFilter so that session state is actually backed by
    // the JDBC SPRING_SESSION table — otherwise SessionInvalidationService's deleteById calls would
    // have no observable effect on the test's in-memory session object.
    private static final String SESSION_COOKIE_NAME = "SESSION";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc sessionAwareMockMvc;
    private Cookie adminCookie;
    private Cookie bobCookie;
    private Cookie bobCookieDevice2;
    private Cookie bobCookie2;
    private long bobId;

    @BeforeAll
    void setUpSessionAwareMockMvcAndAdminSession() throws Exception {
        Filter sessionRepositoryFilter = webApplicationContext.getBean("springSessionRepositoryFilter", Filter.class);
        sessionAwareMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(sessionRepositoryFilter)
                .apply(springSecurity())
                .defaultRequest(get("").contextPath("/api"))
                .build();

        MvcResult adminLogin = sessionAwareMockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminCookie = adminLogin.getResponse().getCookie(SESSION_COOKIE_NAME);
    }

    @Test
    @Order(1)
    void admin_creates_volunteer_bob() throws Exception {
        MvcResult result = sessionAwareMockMvc.perform(post("/api/admin/users")
                        .cookie(adminCookie)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bob\",\"lastName\":\"Martin\",\"username\":\"bob\",\"password\":\"Password1\",\"role\":\"VOLUNTEER\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode idNode = objectMapper.readTree(result.getResponse().getContentAsString()).get("id");
        assertThat(idNode).as("response body must contain an 'id' field").isNotNull();
        bobId = idNode.asLong();
    }

    @Test
    @Order(2)
    void bob_logs_in_and_session_works() throws Exception {
        bobCookie = loginAsBobAndAssertCookie();

        sessionAwareMockMvc.perform(get("/api/auth/me").cookie(bobCookie))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void bob_logs_in_from_a_second_device() throws Exception {
        bobCookieDevice2 = loginAsBobAndAssertCookie();

        sessionAwareMockMvc.perform(get("/api/auth/me").cookie(bobCookieDevice2))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    void admin_disables_bob() throws Exception {
        sessionAwareMockMvc.perform(put("/api/admin/users/" + bobId + "/disable")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(5)
    void bobs_sessions_on_both_devices_now_get_401() throws Exception {
        sessionAwareMockMvc.perform(get("/api/auth/me").cookie(bobCookie))
                .andExpect(status().isUnauthorized());
        sessionAwareMockMvc.perform(get("/api/auth/me").cookie(bobCookieDevice2))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(6)
    void admin_re_enables_bob() throws Exception {
        sessionAwareMockMvc.perform(put("/api/admin/users/" + bobId + "/enable")
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @Order(7)
    void reusing_old_session_still_fails_bob_must_log_in_again() throws Exception {
        sessionAwareMockMvc.perform(get("/api/auth/me").cookie(bobCookie))
                .andExpect(status().isUnauthorized());

        bobCookie2 = loginAsBobAndAssertCookie();

        sessionAwareMockMvc.perform(get("/api/auth/me").cookie(bobCookie2))
                .andExpect(status().isOk());
    }

    @Test
    @Order(8)
    void admin_deletes_bob() throws Exception {
        sessionAwareMockMvc.perform(delete("/api/admin/users/" + bobId)
                        .cookie(adminCookie)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(9)
    void bobs_session_gets_401_after_deletion() throws Exception {
        sessionAwareMockMvc.perform(get("/api/auth/me").cookie(bobCookie2))
                .andExpect(status().isUnauthorized());
    }

    private Cookie loginAsBobAndAssertCookie() throws Exception {
        MvcResult bobLogin = sessionAwareMockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "bob")
                        .param("password", "Password1"))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = bobLogin.getResponse().getCookie(SESSION_COOKIE_NAME);
        assertThat(cookie).as("login response must set a SESSION cookie").isNotNull();
        return cookie;
    }
}
