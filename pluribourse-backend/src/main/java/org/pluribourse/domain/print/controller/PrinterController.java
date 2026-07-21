package org.pluribourse.domain.print.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.service.PrinterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/printers")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class PrinterController {

    private final PrinterService service;

    @PostMapping
    public ResponseEntity<PrinterDto> create(@Valid @RequestBody CreatePrinterDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));
    }
}
