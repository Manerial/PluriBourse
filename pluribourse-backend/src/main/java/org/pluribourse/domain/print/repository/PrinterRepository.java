package org.pluribourse.domain.print.repository;

import org.pluribourse.domain.print.entity.Printer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

public interface PrinterRepository extends JpaRepository<Printer, Long> {

    @Query("select p.printerBridgeId from Printer p")
    Set<String> findAllPrinterBridgeIds();
}
