package org.pluribourse.domain.item.repository;

import org.pluribourse.domain.item.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LotRepository extends JpaRepository<Lot, Long> {

    /**
     * FR-109 (story 5.8) — force-increments {@code Lot.@Version} inside the caller's transaction as
     * the single serialization point for two terminals racing to sell different members of the same
     * lot. A bulk JPQL update (not {@code em.lock(OPTIMISTIC_FORCE_INCREMENT)}, whose increment is
     * deferred to before-commit and therefore uncatchable inside the service method): it runs the
     * {@code UPDATE} now, taking the row write-lock so the second terminal blocks here. When it then
     * unblocks it either matches 0 rows (version already moved) or, under MariaDB snapshot isolation,
     * fails outright with error 1020 — {@link org.pluribourse.domain.pos.service.PosBasketService}
     * turns both into {@code LotAlreadySoldException}.
     *
     * @return 1 if the version was bumped, 0 if {@code expectedVersion} no longer matches
     */
    @Modifying
    @Query("UPDATE Lot l SET l.version = l.version + 1 WHERE l.id = :id AND l.version = :expectedVersion")
    int bumpVersion(@Param("id") Long id, @Param("expectedVersion") long expectedVersion);
}
