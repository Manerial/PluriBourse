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

@RestController
@RequestMapping("/printers")
@PreAuthorize("hasRole('VOLUNTEER')")
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
