package org.pluribourse.seller.repository;

import org.pluribourse.seller.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SellerRepository extends JpaRepository<SellerProfile, Long> {

    List<SellerProfile> findAllByEditionId(Long editionId);

    boolean existsByEditionIdAndEmailIgnoreCase(Long editionId, String email);

    /**
     * Serializes concurrent item creations for the same seller (FR-026): the second caller blocks
     * here until the first commits its item insert, so its own read of {@code nextItemNumber}
     * happens after the first has already incremented it — same rationale as
     * EditionRepository.lockById for seller numbers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SellerProfile s WHERE s.id = :id")
    Optional<SellerProfile> lockById(@Param("id") Long id);

    @Query("""
            SELECT s FROM SellerProfile s
            WHERE s.edition.id = :editionId
              AND (LOWER(s.firstName) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
                   OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
                   OR LOWER(s.email) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\')
            """)
    List<SellerProfile> searchByEditionIdAndQuery(@Param("editionId") Long editionId, @Param("query") String query);
}
