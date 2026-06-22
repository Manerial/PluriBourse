package org.pluribourse.user.services;

import lombok.*;
import org.pluribourse.shared.exception.*;
import org.pluribourse.user.dtos.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.pluribourse.user.mappers.*;
import org.pluribourse.user.repositories.*;
import org.springframework.dao.*;
import org.springframework.http.*;
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

    @Transactional
    public PluriBourseUserDetails changePassword(Long userId, String newRawPassword) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
        user.setPassword(passwordEncoder.encode(newRawPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);
        return new PluriBourseUserDetails(user);
    }

    @Transactional(readOnly = true)
    public List<UserDto> listVolunteers() {
        return userRepository.findByRole(Role.VOLUNTEER)
                .stream().map(userMapper::toDto).toList();
    }

    @Transactional
    public UserDto createVolunteer(CreateUserDto dto) {
        if (userRepository.existsByUsername(dto.username())) {
            throw new BusinessException(HttpStatus.CONFLICT, "username-already-taken", "Username already taken");
        }
        var user = new User();
        user.setFirstName(dto.firstName());
        user.setLastName(dto.lastName());
        user.setUsername(dto.username());
        user.setPassword(passwordEncoder.encode(dto.password()));
        user.setRole(Role.VOLUNTEER);
        user.setPreferredLanguage(Language.FR);
        user.setForcePasswordChange(false);
        user.setEnabled(true);
        try {
            return userMapper.toDto(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "username-already-taken", "Username already taken");
        }
    }

    @Transactional
    public void resetVolunteerPassword(Long id, String newPassword) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
        if (user.getRole() != Role.VOLUNTEER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "cannot-modify-admin", "Admin account cannot be modified");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(true);
        userRepository.save(user);
    }

    @Transactional
    public void disableVolunteer(Long id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
        if (user.getRole() != Role.VOLUNTEER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "cannot-disable-admin", "Admin account cannot be disabled");
        }
        user.setEnabled(false);
        userRepository.save(user);
    }

    @Transactional
    public void enableVolunteer(Long id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
        if (user.getRole() != Role.VOLUNTEER) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "cannot-enable-admin", "Admin account cannot be modified");
        }
        user.setEnabled(true);
        userRepository.save(user);
    }
}
