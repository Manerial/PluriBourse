package org.pluribourse.edition.repository;

import org.pluribourse.edition.entity.*;
import org.springframework.data.jpa.repository.*;

import java.util.*;

public interface EditionRepository extends JpaRepository<Edition, Long> {
    boolean existsByPhaseIn(List<PhaseType> phases);

    List<Edition> findAllByOrderByCreatedAtDesc();

    Optional<Edition> findFirstByPhaseIn(List<PhaseType> phases);
}
