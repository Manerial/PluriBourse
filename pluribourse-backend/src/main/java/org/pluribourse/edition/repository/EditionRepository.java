package org.pluribourse.edition.repository;

import org.pluribourse.edition.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.*;

public interface EditionRepository extends JpaRepository<Edition, Long> {
    boolean existsByPhaseIn(List<PhaseType> phases);

    List<Edition> findAllByOrderByCreatedAtDesc();

    Optional<Edition> findFirstByPhaseIn(List<PhaseType> phases);

    /**
     * Serializes concurrent seller creations within the same edition (FR-026): the second caller
     * blocks here until the first commits, so its read of the persisted {@code nextSellerNumber}
     * counter reflects the first caller's increment — otherwise two simultaneous first-sellers
     * could both read the same counter value and pick the same seller number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Edition e WHERE e.id = :id")
    Optional<Edition> lockById(@Param("id") Long id);
}
