package org.pluribourse.domain.edition.repository;

import org.pluribourse.domain.edition.entity.EditionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface EditionCategoryRepository extends JpaRepository<EditionCategory, Long> {

    @Query("SELECT c FROM EditionCategory c LEFT JOIN FETCH c.tableNumbers WHERE c.edition.id = :editionId ORDER BY c.displayOrder ASC, c.name ASC")
    List<EditionCategory> findAllByEditionIdWithTables(@Param("editionId") Long editionId);

    boolean existsByEditionId(Long editionId);

    /**
     * Serializes concurrent table assignments (FR-023) targeting the same category: the second
     * caller blocks here until the first commits its item insert, so its own load count reflects
     * the first item — without this, two simultaneous first-deposits into the same category could
     * both read a load count of zero and pick the same least-loaded table independently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EditionCategory c WHERE c.id = :id")
    Optional<EditionCategory> lockById(@Param("id") Long id);

    /**
     * Bulk JPQL delete so the SQL DELETE executes immediately within the current transaction,
     * before subsequent inserts. Derived-delete would schedule DELETEs after INSERTs at flush
     * time, violating the (edition_id, name) unique constraint on re-save.
     * clearAutomatically=true purges the 1st-level cache so subsequent save() calls see fresh state.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM EditionCategory c WHERE c.edition.id = :editionId")
    void deleteAllByEditionId(@Param("editionId") Long editionId);
}
