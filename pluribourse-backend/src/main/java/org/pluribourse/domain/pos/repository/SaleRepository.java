package org.pluribourse.domain.pos.repository;

import org.pluribourse.domain.pos.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleRepository extends JpaRepository<Sale, Long> {
}
