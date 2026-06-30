package org.pluribourse.edition.repository;

import org.pluribourse.edition.entity.EditionCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EditionCategoryRepository extends JpaRepository<EditionCategory, Long> {

    @Query("SELECT c FROM EditionCategory c LEFT JOIN FETCH c.tableNumbers WHERE c.edition.id = :editionId ORDER BY c.displayOrder ASC, c.name ASC")
    List<EditionCategory> findAllByEditionIdWithTables(@Param("editionId") Long editionId);

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
