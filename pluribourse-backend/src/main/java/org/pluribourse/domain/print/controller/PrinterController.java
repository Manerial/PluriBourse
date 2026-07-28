package org.pluribourse.domain.print.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.DiscoveredPrinterDto;
import org.pluribourse.domain.print.dto.IgnoredPrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.dto.PrinterSummaryDto;
import org.pluribourse.domain.print.service.PrintResult;
import org.pluribourse.domain.print.service.PrinterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/printers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PrinterController {

    private final PrinterService service;

    @GetMapping
    public List<PrinterSummaryDto> list() {
        return service.list();
    }

    @GetMapping("/discovered")
    public List<DiscoveredPrinterDto> discover() {
        return service.discover();
    }

    @PostMapping("/discovered/{printerBridgeId}/ignore")
    public ResponseEntity<Void> ignore(@PathVariable String printerBridgeId) {
        service.ignore(printerBridgeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ignored")
    public List<IgnoredPrinterDto> listIgnored() {
        return service.listIgnored();
    }

    @DeleteMapping("/ignored/{printerBridgeId}")
    public ResponseEntity<Void> reactivate(@PathVariable String printerBridgeId) {
        service.reactivate(printerBridgeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<PrinterDto> create(@Valid @RequestBody CreatePrinterDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }

    @PostMapping("/{id}/test-print")
    public PrintResult testPrint(@PathVariable Long id) {
        return service.testPrint(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
