package org.pluribourse.domain.print.repository;

import org.pluribourse.domain.print.entity.IgnoredPrinter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Set;

public interface IgnoredPrinterRepository extends JpaRepository<IgnoredPrinter, Long> {

    @Query("select p.printerBridgeId from IgnoredPrinter p")
    Set<String> findAllPrinterBridgeIds();

    Optional<IgnoredPrinter> findByPrinterBridgeId(String printerBridgeId);
}
