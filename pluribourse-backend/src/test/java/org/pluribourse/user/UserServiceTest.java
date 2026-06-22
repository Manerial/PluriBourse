package org.pluribourse.user;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.pluribourse.shared.exception.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.mappers.*;
import org.pluribourse.user.repositories.*;
import org.pluribourse.user.services.*;
import org.springframework.http.*;
import org.springframework.security.crypto.password.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    // ── changePassword ────────────────────────────────────────────────────────

    @Test
    void changePassword_encodes_password_and_clears_force_flag() {
        var user = new User();
        user.setUsername("Admin");
        user.setPassword("oldEncoded");
        user.setRole(Role.ADMIN);
        user.setPreferredLanguage(Language.FR);
        user.setForcePasswordChange(true);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString())).thenReturn("newEncoded");
        when(userRepository.save(any())).thenReturn(user);

        PluriBourseUserDetails result = userService.changePassword(1L, "newPassword");

        assertThat(user.getPassword()).isEqualTo("newEncoded");
        assertThat(user.isForcePasswordChange()).isFalse();
        assertThat(result.isForcePasswordChange()).isFalse();
        assertThat(result.getUsername()).isEqualTo("Admin");
        verify(passwordEncoder).encode("newPassword");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_throws_BusinessException_when_user_not_found() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.changePassword(99L, "anyPassword"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("User not found");
    }

    // ── listVolunteers ────────────────────────────────────────────────────────

    @Test
    void listVolunteers_returns_only_volunteer_accounts() {
        var volunteer = volunteerUser(1L, "alice");
        var dto = new UserDto(1L, "Alice", "Smith", "alice", "VOLUNTEER", true);
        when(userRepository.findByRole(Role.VOLUNTEER)).thenReturn(List.of(volunteer));
        when(userMapper.toDto(volunteer)).thenReturn(dto);

        var result = userService.listVolunteers();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().role()).isEqualTo("VOLUNTEER");
    }

    // ── createVolunteer ───────────────────────────────────────────────────────

    @Test
    void createVolunteer_sets_role_volunteer_and_enabled_true() {
        var dto = new CreateUserDto("Alice", "Smith", "alice", "Password1");
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("Password1")).thenReturn("encoded");
        var savedUser = volunteerUser(1L, "alice");
        when(userRepository.save(any())).thenReturn(savedUser);
        var expected = new UserDto(1L, "Alice", "Smith", "alice", "VOLUNTEER", true);
        when(userMapper.toDto(savedUser)).thenReturn(expected);

        var result = userService.createVolunteer(dto);

        assertThat(result.role()).isEqualTo("VOLUNTEER");
        assertThat(result.enabled()).isTrue();
        verify(passwordEncoder).encode("Password1");
        var captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().isForcePasswordChange()).isFalse();
    }

    @Test
    void createVolunteer_with_duplicate_username_throws_conflict() {
        var dto = new CreateUserDto("Alice", "Smith", "alice", "Password1");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> userService.createVolunteer(dto))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ── resetVolunteerPassword ────────────────────────────────────────────────

    @Test
    void resetVolunteerPassword_encodes_and_sets_force_flag() {
        var user = volunteerUser(1L, "alice");
        user.setForcePasswordChange(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass1")).thenReturn("encoded");
        when(userRepository.save(any())).thenReturn(user);

        userService.resetVolunteerPassword(1L, "newPass1");

        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.isForcePasswordChange()).isTrue();
        verify(passwordEncoder).encode("newPass1");
    }

    @Test
    void resetVolunteerPassword_throws_not_found_when_user_missing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.resetVolunteerPassword(99L, "pass"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void resetVolunteerPassword_on_admin_throws_forbidden() {
        var admin = new User();
        admin.setUsername("Admin");
        admin.setRole(Role.ADMIN);
        admin.setPreferredLanguage(Language.FR);
        admin.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.resetVolunteerPassword(1L, "newPass"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── disableVolunteer ──────────────────────────────────────────────────────

    @Test
    void disableVolunteer_sets_enabled_false() {
        var user = volunteerUser(1L, "alice");
        user.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.disableVolunteer(1L);

        assertThat(user.getEnabled()).isFalse();
    }

    @Test
    void disableVolunteer_on_admin_throws_forbidden() {
        var admin = new User();
        admin.setUsername("Admin");
        admin.setRole(Role.ADMIN);
        admin.setPreferredLanguage(Language.FR);
        admin.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.disableVolunteer(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void disableVolunteer_throws_not_found_when_user_missing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.disableVolunteer(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── enableVolunteer ───────────────────────────────────────────────────────

    @Test
    void enableVolunteer_sets_enabled_true() {
        var user = volunteerUser(1L, "alice");
        user.setEnabled(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.enableVolunteer(1L);

        assertThat(user.getEnabled()).isTrue();
    }

    @Test
    void enableVolunteer_throws_not_found_when_user_missing() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.enableVolunteer(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void enableVolunteer_on_admin_throws_forbidden() {
        var admin = new User();
        admin.setUsername("Admin");
        admin.setRole(Role.ADMIN);
        admin.setPreferredLanguage(Language.FR);
        admin.setEnabled(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.enableVolunteer(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private User volunteerUser(Long id, String username) {
        var user = new User();
        user.setUsername(username);
        user.setFirstName("Alice");
        user.setLastName("Smith");
        user.setPassword("encoded");
        user.setRole(Role.VOLUNTEER);
        user.setPreferredLanguage(Language.FR);
        user.setEnabled(true);
        user.setForcePasswordChange(false);
        return user;
    }
}
