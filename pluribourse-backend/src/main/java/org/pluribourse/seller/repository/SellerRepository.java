package org.pluribourse.seller.repository;

import org.pluribourse.seller.entity.SellerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SellerRepository extends JpaRepository<SellerProfile, Long> {

    List<SellerProfile> findAllByEditionId(Long editionId);

    boolean existsByEditionIdAndEmailIgnoreCase(Long editionId, String email);

    @Query("""
            SELECT s FROM SellerProfile s
            WHERE s.edition.id = :editionId
              AND (LOWER(s.firstName) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
                   OR LOWER(s.lastName) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\'
                   OR LOWER(s.email) LIKE LOWER(CONCAT('%', :query, '%')) ESCAPE '\\')
            """)
    List<SellerProfile> searchByEditionIdAndQuery(@Param("editionId") Long editionId, @Param("query") String query);
}
