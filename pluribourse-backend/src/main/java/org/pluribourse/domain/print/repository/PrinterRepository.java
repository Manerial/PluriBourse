package org.pluribourse.domain.print.repository;

import org.pluribourse.domain.print.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
}
