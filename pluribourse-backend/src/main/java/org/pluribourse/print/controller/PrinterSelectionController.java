package org.pluribourse.print.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.pluribourse.print.dto.AvailablePrinterDto;
import org.pluribourse.print.dto.PrinterSelectionDto;
import org.pluribourse.print.dto.PrinterSelectionStatusDto;
import org.pluribourse.print.service.PrinterSelectionService;
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
    public PrinterSelectionStatusDto getSelection(HttpServletRequest request) {
        return service.getStatus(request.getSession());
    }

    @PostMapping("/selection")
    public PrinterSelectionStatusDto selectPrinters(@Valid @RequestBody PrinterSelectionDto dto, HttpServletRequest request) {
        return service.selectPrinters(request.getSession(), dto);
    }
}
