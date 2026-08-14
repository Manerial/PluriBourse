package org.pluribourse.domain.payout.repository;

import org.pluribourse.domain.payout.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    Optional<Settlement> findBySellerProfileId(Long sellerProfileId);

    List<Settlement> findAllBySellerProfileEditionId(Long editionId);
}
