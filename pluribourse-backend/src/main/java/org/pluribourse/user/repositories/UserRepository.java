package org.pluribourse.user.repositories;

import org.pluribourse.user.entities.*;
import org.pluribourse.user.enums.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByRole(Role role);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);
}
