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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCreateRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private AdminCliSupport support;
    @InjectMocks
    private AdminCreateRunner runner;

    @Test
    void run_without_create_flag_does_nothing() throws Exception {
        ApplicationArguments args = mock(ApplicationArguments.class);
        when(args.containsOption("create-admin")).thenReturn(false);

        runner.run(args);

        verifyNoInteractions(userRepository, passwordEncoder, support, applicationContext);
    }

    @Test
    void performCreate_creates_admin_directly_when_none_exist() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByUsername("NewAdmin")).thenReturn(false);
        when(support.generatePassword()).thenReturn("TempPass1234");
        when(passwordEncoder.encode("TempPass1234")).thenReturn("encodedTemp");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        runner.performCreate("NewAdmin");

        verify(support, never()).requireAdminAuth();
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("NewAdmin");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().isForcePasswordChange()).isTrue();
        assertThat(captor.getValue().getPassword()).isEqualTo("encodedTemp");
    }

    @Test
    void performCreate_requires_auth_when_admin_already_exists() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        when(userRepository.existsByUsername("NewAdmin")).thenReturn(false);
        when(support.generatePassword()).thenReturn("TempPass1234");
        when(passwordEncoder.encode(any())).thenReturn("encodedTemp");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        runner.performCreate("NewAdmin");

        verify(support).requireAdminAuth();
        verify(userRepository).save(any());
    }

    @Test
    void performCreate_throws_when_username_already_taken() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByUsername("Admin")).thenReturn(true);

        assertThatThrownBy(() -> runner.performCreate("Admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin")
                .hasMessageContaining("already taken");
    }
}
