package org.pluribourse.domain.instanceconfig.repository;

import org.pluribourse.domain.instanceconfig.entity.*;
import org.springframework.data.jpa.repository.*;

public interface GlobalInstanceConfigRepository extends JpaRepository<GlobalInstanceConfig, Long> {
}
