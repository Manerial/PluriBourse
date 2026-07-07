package org.pluribourse.print.repository;

import org.pluribourse.print.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
}
