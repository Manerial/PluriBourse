package org.pluribourse.instanceconfig.repository;

import org.pluribourse.instanceconfig.entity.*;
import org.springframework.data.jpa.repository.*;

public interface GlobalInstanceConfigRepository extends JpaRepository<GlobalInstanceConfig, Long> {
}
