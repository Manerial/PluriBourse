package org.pluribourse.domain.print.controller;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.dto.PrinterStatusDto;
import org.pluribourse.domain.print.service.PrintQueueDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/print-queue")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PrintQueueController {

    private final PrintQueueDiagnosticsService service;

    @GetMapping
    public List<PrinterStatusDto> listStatuses() {
        return service.listStatuses();
    }

    @PostMapping("/refresh")
    public List<PrinterStatusDto> refreshStatuses() {
        return service.refreshStatuses();
    }

    @PostMapping("/{printerId}/resume")
    public ResponseEntity<Void> resume(@PathVariable Long printerId) {
        service.resumeQueue(printerId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{printerId}/discard")
    public ResponseEntity<Void> discard(@PathVariable Long printerId) {
        service.discardFailedJob(printerId);
        return ResponseEntity.noContent().build();
    }
}
