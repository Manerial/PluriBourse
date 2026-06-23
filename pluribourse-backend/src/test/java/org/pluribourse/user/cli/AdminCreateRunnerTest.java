package org.pluribourse.user.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pluribourse.user.entities.User;
import org.pluribourse.user.enums.Language;
import org.pluribourse.user.enums.Role;
import org.pluribourse.user.repositories.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCreateRunnerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationContext applicationContext;
    @Mock private AdminCliSupport support;
    @InjectMocks private AdminCreateRunner runner;

    @Test
    void run_without_create_flag_does_nothing() throws Exception {
        var args = mock(ApplicationArguments.class);
        when(args.containsOption("create-admin")).thenReturn(false);

        runner.run(args);

        verifyNoInteractions(userRepository, passwordEncoder, support, applicationContext);
    }

    @Test
    void performCreate_creates_admin_directly_when_none_exist() {
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());
        when(userRepository.existsByUsername("NewAdmin")).thenReturn(false);
        when(support.generatePassword()).thenReturn("TempPass1234");
        when(passwordEncoder.encode("TempPass1234")).thenReturn("encodedTemp");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        runner.performCreate("NewAdmin");

        verify(support, never()).requireAdminAuth();
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("NewAdmin");
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(captor.getValue().isForcePasswordChange()).isTrue();
        assertThat(captor.getValue().getPassword()).isEqualTo("encodedTemp");
    }

    @Test
    void performCreate_requires_auth_when_admin_already_exists() {
        var existing = new User();
        existing.setUsername("Admin");
        existing.setRole(Role.ADMIN);
        existing.setPreferredLanguage(Language.FR);

        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(existing));
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
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of());
        when(userRepository.existsByUsername("Admin")).thenReturn(true);

        assertThatThrownBy(() -> runner.performCreate("Admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin")
                .hasMessageContaining("already taken");
    }
}
