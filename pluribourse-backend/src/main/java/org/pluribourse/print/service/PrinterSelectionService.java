package org.pluribourse.print.service;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.pluribourse.print.dto.AvailablePrinterDto;
import org.pluribourse.print.dto.PrinterSelectionDto;
import org.pluribourse.print.dto.PrinterSelectionStatusDto;
import org.pluribourse.print.entity.Printer;
import org.pluribourse.print.entity.PrinterType;
import org.pluribourse.print.exception.InvalidPrinterSelectionException;
import org.pluribourse.print.exception.PrinterNotFoundException;
import org.pluribourse.print.repository.PrinterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Session-only printer selection (FR-098) — never persisted, see story 3.9 Dev Notes
 * § Pourquoi pas de migration Liquibase. {@link #getSelectedPrinterId(HttpSession, PrinterType)}
 * is the sole contract stories 3.5/3.6 must use to resolve a job's target printer.
 */
@Service
@RequiredArgsConstructor
public class PrinterSelectionService {

    private static final String THERMAL_SESSION_ATTRIBUTE = "printerSelection.thermalPrinterId";
    private static final String A4_SESSION_ATTRIBUTE = "printerSelection.a4PrinterId";
    private static final String DONE_SESSION_ATTRIBUTE = "printerSelection.done";

    private final PrinterRepository printerRepository;
    private final PrintQueueService printQueueService;

    public List<AvailablePrinterDto> listAvailablePrinters() {
        return printerRepository.findAll().stream()
                .filter(this::isAvailable)
                .map(printer -> new AvailablePrinterDto(printer.getId(), printer.getName(), printer.getType()))
                .toList();
    }

    public PrinterSelectionStatusDto getStatus(HttpSession session) {
        Boolean done = (Boolean) session.getAttribute(DONE_SESSION_ATTRIBUTE);
        Long thermalPrinterId = (Long) session.getAttribute(THERMAL_SESSION_ATTRIBUTE);
        Long a4PrinterId = (Long) session.getAttribute(A4_SESSION_ATTRIBUTE);
        return new PrinterSelectionStatusDto(Boolean.TRUE.equals(done), thermalPrinterId, a4PrinterId);
    }

    public PrinterSelectionStatusDto selectPrinters(HttpSession session, PrinterSelectionDto dto) {
        if (dto.thermalPrinterId() != null) {
            validateSelection(dto.thermalPrinterId(), PrinterType.THERMAL);
        }
        if (dto.a4PrinterId() != null) {
            validateSelection(dto.a4PrinterId(), PrinterType.A4);
        }
        session.setAttribute(THERMAL_SESSION_ATTRIBUTE, dto.thermalPrinterId());
        session.setAttribute(A4_SESSION_ATTRIBUTE, dto.a4PrinterId());
        session.setAttribute(DONE_SESSION_ATTRIBUTE, Boolean.TRUE);
        return getStatus(session);
    }

    public Optional<Long> getSelectedPrinterId(HttpSession session, PrinterType type) {
        String attribute = type == PrinterType.THERMAL ? THERMAL_SESSION_ATTRIBUTE : A4_SESSION_ATTRIBUTE;
        return Optional.ofNullable((Long) session.getAttribute(attribute));
    }

    private void validateSelection(Long printerId, PrinterType expectedType) {
        Printer printer = printerRepository.findById(printerId)
                .orElseThrow(() -> new PrinterNotFoundException(printerId));
        if (printer.getType() != expectedType) {
            throw new InvalidPrinterSelectionException(
                    "Printer " + printerId + " is not of type " + expectedType);
        }
        if (!isAvailable(printer)) {
            throw new InvalidPrinterSelectionException(
                    "Printer " + printerId + " is not currently available");
        }
    }

    private boolean isAvailable(Printer printer) {
        PrinterQueueHandle handle = printQueueService.getHandle(printer.getId());
        return handle != null && !handle.isSuspended() && handle.getLastError() == null;
    }
}
