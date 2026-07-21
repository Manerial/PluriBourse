package org.pluribourse.domain.user.cli;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.pluribourse.domain.user.entities.*;
import org.pluribourse.domain.user.enums.*;
import org.pluribourse.domain.user.repositories.*;
import org.springframework.boot.*;
import org.springframework.context.*;
import org.springframework.security.crypto.password.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminPasswordResetRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private AdminCliSupport support;
    @InjectMocks
    private AdminPasswordResetRunner runner;

    @Test
    void run_without_reset_flag_does_nothing() throws Exception {
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.containsOption("reset-admin-password")).thenReturn(false);

        runner.run(args);

        verifyNoInteractions(userRepository, passwordEncoder, support, applicationContext);
    }

    @Test
    void performReset_resets_single_admin_without_auth() {
        User admin = new User();
        admin.setUsername("Admin");
        admin.setRole(Role.ADMIN);
        admin.setPreferredLanguage(Language.FR);
        admin.setPassword("oldEncoded");
        admin.setForcePasswordChange(false);

        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));
        when(support.generatePassword()).thenReturn("TempPass1234");
        when(passwordEncoder.encode("TempPass1234")).thenReturn("newEncoded");
        when(userRepository.save(any())).thenReturn(admin);

        runner.performReset("Admin");

        verify(support, never()).requireAdminAuth();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isForcePasswordChange()).isTrue();
        assertThat(captor.getValue().getPassword()).isEqualTo("newEncoded");
    }

    @Test
    void performReset_requires_auth_when_multiple_admins_exist() {
        User admin1 = new User();
        admin1.setUsername("Admin1");
        admin1.setRole(Role.ADMIN);
        admin1.setPreferredLanguage(Language.FR);
        admin1.setPassword("encoded1");

        User admin2 = new User();
        admin2.setUsername("Admin2");
        admin2.setRole(Role.ADMIN);
        admin2.setPreferredLanguage(Language.FR);
        admin2.setPassword("encoded2");

        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin1, admin2));
        when(support.generatePassword()).thenReturn("TempPass1234");
        when(passwordEncoder.encode(any())).thenReturn("newEncoded");
        when(userRepository.save(any())).thenReturn(admin1);

        runner.performReset("Admin1");

        verify(support).requireAdminAuth();
        verify(userRepository).save(admin1);
    }

    @Test
    void performReset_throws_when_no_admin_found() {
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());

        assertThatThrownBy(() -> runner.performReset("Admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("--create-admin");
    }

    @Test
    void performReset_throws_when_login_not_found() {
        User admin = new User();
        admin.setUsername("Admin");
        admin.setRole(Role.ADMIN);
        admin.setPreferredLanguage(Language.FR);
        admin.setPassword("encoded");

        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(admin));

        assertThatThrownBy(() -> runner.performReset("WrongLogin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WrongLogin");
    }
}
