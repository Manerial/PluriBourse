package org.pluribourse.domain.user.services;

import lombok.*;
import org.jspecify.annotations.NonNull;
import org.pluribourse.domain.user.dtos.*;
import org.pluribourse.domain.user.entities.*;
import org.pluribourse.domain.user.enums.*;
import org.pluribourse.domain.user.exception.*;
import org.pluribourse.domain.user.mappers.*;
import org.pluribourse.domain.user.repositories.*;
import org.pluribourse.shared.security.*;
import org.springframework.dao.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final SessionInvalidationService sessionInvalidationService;

    private @NonNull User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    @Transactional
    public PluriBourseUserDetails changePassword(Long userId, String newRawPassword) {
        User user = getUser(userId);
        user.setPassword(passwordEncoder.encode(newRawPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);
        return new PluriBourseUserDetails(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto> listVolunteers() {
        return userRepository.findByRole(Role.VOLUNTEER).stream()
                .map(userMapper::toDto)
                .toList();
    }

    @Transactional
    public UserDto createUser(CreateUserDto dto) {
        if (userRepository.existsByUsername(dto.username())) {
            throw new UsernameTakenException();
        }
        User user = userMapper.toEntity(dto);
        user.setPassword(passwordEncoder.encode(dto.password()));
        try {
            return userMapper.toDto(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            throw new UsernameTakenException();
        }
    }

    @Transactional
    public void resetUserPassword(Long id, String newPassword) {
        User user = getUser(id);
        if (user.getRole() == Role.ADMIN) {
            throw new CannotModifyAdminException();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(true);
        userRepository.save(user);
    }

    @Transactional
    public void disableUser(Long id) {
        User user = getUser(id);
        if (user.getRole() == Role.ADMIN) {
            throw new CannotDisableAdminException();
        }
        user.setEnabled(false);
        userRepository.save(user);
        sessionInvalidationService.invalidateSessionsFor(user.getUsername());
    }

    @Transactional
    public void enableUser(Long id) {
        User user = getUser(id);
        if (user.getRole() == Role.ADMIN) {
            throw new CannotEnableAdminException();
        }
        user.setEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getUser(id);
        if (user.getRole() == Role.ADMIN) {
            throw new CannotDeleteAdminException();
        }
        String username = user.getUsername();
        userRepository.delete(user);
        sessionInvalidationService.invalidateSessionsFor(username);
    }

    @Transactional
    public PluriBourseUserDetails updateLanguagePreference(Long userId, Language lang) {
        User user = getUser(userId);
        user.setPreferredLanguage(lang);
        user.setLanguageInitialized(true);
        userRepository.save(user);
        return new PluriBourseUserDetails(user);
    }
}
