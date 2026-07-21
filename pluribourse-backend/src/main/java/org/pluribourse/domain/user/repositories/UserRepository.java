package org.pluribourse.domain.user.repositories;

import org.pluribourse.domain.user.entities.*;
import org.pluribourse.domain.user.enums.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByRole(Role role);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);
}
