package org.pluribourse.domain.user.services;

import lombok.*;
import org.jspecify.annotations.*;
import org.pluribourse.domain.user.entities.*;
import org.pluribourse.domain.user.repositories.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.*;
import org.springframework.transaction.annotation.*;

@NullMarked
@Service
@RequiredArgsConstructor
public class PluriBourseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(PluriBourseUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
