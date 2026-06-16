package org.pluribourse.user;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.*;
import org.mockito.junit.jupiter.*;
import org.pluribourse.shared.exception.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.repositories.*;
import org.pluribourse.user.services.*;
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

    @InjectMocks
    private UserService userService;

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
}
