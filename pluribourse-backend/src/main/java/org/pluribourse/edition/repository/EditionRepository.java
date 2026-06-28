package org.pluribourse.edition.repository;

import org.pluribourse.edition.entity.Edition;
import org.pluribourse.edition.entity.PhaseType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EditionRepository extends JpaRepository<Edition, Long> {
    boolean existsByPhaseIn(List<PhaseType> phases);
    List<Edition> findAllByOrderByCreatedAtDesc();
}
