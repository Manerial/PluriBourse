package org.pluribourse.user;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.pluribourse.shared.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.repositories.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.context.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.*;
import org.springframework.test.web.servlet.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LanguagePreferenceIT extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageSource messageSource;

    private MockHttpSession adminSession;
    private MockHttpSession volunteerSession;

    // Used across Order(9) / Order(10) / Order(11)
    private String frUserUsername;
    private String enUserUsername;

    @BeforeAll
    void setUpSessions() throws Exception {
        // parent @BeforeAll (setUpMockMvc) already ran — mockMvc is available
        MvcResult adminLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "test_admin")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        adminSession = (MockHttpSession) adminLogin.getRequest().getSession(false);

        // Volunteer login requires an active edition (Story 2.3 gate). Keep it active for the whole
        // class so that re-logins in @Order(9)/@Order(10)/@Order(12) also succeed.
        mockMvc.perform(post("/api/admin/editions")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Setup Edition\"}"))
                .andExpect(status().isCreated());

        MvcResult volunteerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andReturn();
        volunteerSession = (MockHttpSession) volunteerLogin.getRequest().getSession(false);
    }

    @Test @Order(1)
    void get_me_returns_preferredLanguage() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("FR"));
    }

    @Test @Order(2)
    void volunteer_get_me_returns_preferredLanguage() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").exists());
    }

    @Test @Order(3)
    void update_language_en_as_volunteer() throws Exception {
        mockMvc.perform(put("/api/account/language-preference")
                        .session(volunteerSession)
                        .with(csrf())
                        .param("language", "EN"))
                .andExpect(status().isOk());
    }

    @Test @Order(4)
    void get_me_after_update_returns_en() throws Exception {
        mockMvc.perform(get("/api/auth/me").session(volunteerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("EN"));
    }

    @Test @Order(5)
    void update_language_back_to_fr() throws Exception {
        mockMvc.perform(put("/api/account/language-preference")
                        .session(volunteerSession)
                        .with(csrf())
                        .param("language", "FR"))
                .andExpect(status().isOk());
    }

    @Test @Order(6)
    void unauthenticated_put_returns_401() throws Exception {
        mockMvc.perform(put("/api/account/language-preference")
                        .with(csrf())
                        .param("language", "EN"))
                .andExpect(status().isUnauthorized());
    }

    @Test @Order(7)
    void invalid_language_code_returns_400() throws Exception {
        mockMvc.perform(put("/api/account/language-preference")
                        .session(volunteerSession)
                        .with(csrf())
                        .param("language", "DE"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(8)
    void lowercase_language_returns_400() throws Exception {
        mockMvc.perform(put("/api/account/language-preference")
                        .session(volunteerSession)
                        .with(csrf())
                        .param("language", "en"))
                .andExpect(status().isBadRequest());
    }

    @Test @Order(9)
    void first_login_sets_fr_from_accept_language() throws Exception {
        frUserUsername = "lang_test_fr_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(frUserUsername);
        user.setPassword(passwordEncoder.encode("Admin"));
        user.setRole(Role.VOLUNTEER);
        user.setPreferredLanguage(Language.EN);
        user.setLanguageInitialized(false);
        user.setForcePasswordChange(false);
        user.setEnabled(true);
        userRepository.save(user);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Accept-Language", "fr-FR,fr;q=0.9")
                        .param("username", frUserUsername)
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("FR"))
                .andReturn();

        User saved = userRepository.findByUsername(frUserUsername).orElseThrow();
        assertThat(saved.getPreferredLanguage()).isEqualTo(Language.FR);
        assertThat(saved.isLanguageInitialized()).isTrue();
    }

    @Test @Order(10)
    void first_login_sets_en_for_unsupported_language() throws Exception {
        enUserUsername = "lang_test_de_" + UUID.randomUUID().toString().substring(0, 8);
        User user = new User();
        user.setUsername(enUserUsername);
        user.setPassword(passwordEncoder.encode("Admin"));
        user.setRole(Role.VOLUNTEER);
        user.setPreferredLanguage(Language.FR);
        user.setLanguageInitialized(false);
        user.setForcePasswordChange(false);
        user.setEnabled(true);
        userRepository.save(user);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Accept-Language", "de-DE,de;q=0.9")
                        .param("username", enUserUsername)
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("EN"));

        User saved = userRepository.findByUsername(enUserUsername).orElseThrow();
        assertThat(saved.getPreferredLanguage()).isEqualTo(Language.EN);
        assertThat(saved.isLanguageInitialized()).isTrue();
    }

    @Test @Order(11)
    void subsequent_login_preserves_preference() throws Exception {
        // frUserUsername was created in Order(9) with languageInitialized=true and preferredLanguage=FR
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .param("username", frUserUsername)
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("FR"));
    }

    @Test @Order(12)
    void preference_survives_logout_and_relogin() throws Exception {
        // AC3: update preference → logout → re-login → same language returned
        mockMvc.perform(put("/api/account/language-preference")
                        .session(volunteerSession)
                        .with(csrf())
                        .param("language", "EN"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .session(volunteerSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        MvcResult reLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "volunteer1")
                        .param("password", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("EN"))
                .andReturn();

        // Restore FR for test isolation
        MockHttpSession freshSession = (MockHttpSession) reLogin.getRequest().getSession(false);
        mockMvc.perform(put("/api/account/language-preference")
                        .session(freshSession)
                        .with(csrf())
                        .param("language", "FR"))
                .andExpect(status().isOk());
    }

    @Test @Order(13)
    void message_source_resolves_en() {
        String appName = messageSource.getMessage("app.name", null, Locale.ENGLISH);
        assertThat(appName).isEqualTo("PluriBourse");
    }

    @Test @Order(14)
    void message_source_resolves_fr() {
        String appName = messageSource.getMessage("app.name", null, Locale.FRENCH);
        assertThat(appName).isEqualTo("PluriBourse");
    }
}
