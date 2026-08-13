package org.pluribourse.domain.pos.repository;

import org.pluribourse.domain.pos.entity.Basket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BasketRepository extends JpaRepository<Basket, Long> {

    Optional<Basket> findByEditionIdAndUserId(Long editionId, Long userId);

    List<Basket> findAllByEditionId(Long editionId);
}
