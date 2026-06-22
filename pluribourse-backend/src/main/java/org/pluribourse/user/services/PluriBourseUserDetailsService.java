package org.pluribourse.user.services;

import lombok.*;
import org.jspecify.annotations.*;
import org.pluribourse.user.entities.*;
import org.pluribourse.user.repositories.*;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.*;

@NullMarked
@Service
@RequiredArgsConstructor
public class PluriBourseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(PluriBourseUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
