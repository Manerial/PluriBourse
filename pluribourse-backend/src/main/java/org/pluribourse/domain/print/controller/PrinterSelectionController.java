package org.pluribourse.domain.print.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.dto.AvailablePrinterDto;
import org.pluribourse.domain.print.dto.PrinterSelectionDto;
import org.pluribourse.domain.print.service.PrinterSelectionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Story 5.2 (AC 5) widened this from VOLUNTEER-only to VOLUNTEER+ADMIN: an admin needs to select an
 * A4 printer to print a seller's sales report from {@code /admin/settlement}, the same interstitial
 * (FR-098) a volunteer already uses. Still not open to SELLER (blocked globally, see SecurityConfig).
 */
@RestController
@RequestMapping("/printers")
@PreAuthorize("hasAnyRole('VOLUNTEER', 'ADMIN')")
@RequiredArgsConstructor
public class PrinterSelectionController {

    private final PrinterSelectionService service;

    @GetMapping("/available")
    public List<AvailablePrinterDto> listAvailable() {
        return service.listAvailablePrinters();
    }

    @GetMapping("/selection")
    public PrinterSelectionDto getSelection(HttpServletRequest request) {
        return service.getStatus(request.getSession());
    }

    @PostMapping("/selection")
    public PrinterSelectionDto selectPrinters(@Valid @RequestBody PrinterSelectionDto dto, HttpServletRequest request) {
        return service.selectPrinters(request.getSession(), dto);
    }
}
