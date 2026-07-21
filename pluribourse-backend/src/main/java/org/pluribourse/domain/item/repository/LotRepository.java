package org.pluribourse.domain.item.repository;

import org.pluribourse.domain.item.entity.Lot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<Lot, Long> {
}
