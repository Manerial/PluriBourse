package org.pluribourse.shared.instanceconfig.repository;

import org.pluribourse.shared.instanceconfig.entity.GlobalInstanceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalInstanceConfigRepository extends JpaRepository<GlobalInstanceConfig, Long> {
}
