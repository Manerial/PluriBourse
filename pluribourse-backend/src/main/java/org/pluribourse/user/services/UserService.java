package org.pluribourse.user.services;

import lombok.*;
import org.pluribourse.shared.exception.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.repositories.*;
import org.springframework.http.*;
import org.springframework.security.crypto.password.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public PluriBourseUserDetails changePassword(Long userId, String newRawPassword) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "user-not-found", "User not found"));
        user.setPassword(passwordEncoder.encode(newRawPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);
        return new PluriBourseUserDetails(user);
    }
}
