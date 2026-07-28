package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.DiscoveredPrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.dto.PrinterSummaryDto;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterConfigurationException;
import org.pluribourse.domain.print.exception.PrinterNotFoundException;
import org.pluribourse.domain.print.mapper.PrinterMapper;
import org.pluribourse.domain.print.repository.PrinterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrinterService {

    private static final int THERMAL_WIDTH_57 = 57;
    private static final int THERMAL_WIDTH_80 = 80;

    private final PrinterRepository repository;
    private final PrinterMapper mapper;
    private final PrintQueueService printQueueService;
    private final PrinterBridgeClient printerBridgeClient;

    @Transactional
    public PrinterDto create(CreatePrinterDto dto) {
        validateConfiguration(dto);
        Printer printer = mapper.toEntity(dto);
        try {
            printer = repository.save(printer);
        } catch (DataIntegrityViolationException e) {
            throw new InvalidPrinterConfigurationException(
                    "A printer named '" + dto.name() + "' already exists.");
        }
        printQueueService.registerPrinter(printer);
        return mapper.toDto(printer);
    }

    /**
     * Registry listing (story 3.8, AC1) — connection status is read straight from the in-memory
     * handle ({@code getLastError() == null}), not the richer {@link PrinterQueueHandle.ErrorSnapshot}
     * used by the diagnostic view (story 3.7), which also folds in {@code suspended}. The handle can
     * transiently be {@code null} for a printer that is in the process of being deleted — {@link #delete}
     * removes the in-memory handle before its enclosing transaction commits the row deletion, so a
     * concurrent read can still see the row with no handle — treated here as not connected rather than
     * relying on the handle/printer invariant holding at every instant (code review finding, story 3.8).
     */
    public List<PrinterSummaryDto> list() {
        return repository.findAll().stream()
                .map(printer -> {
                    PrinterQueueHandle handle = printQueueService.getHandle(printer.getId());
                    boolean connected = handle != null && handle.getLastError() == null;
                    return new PrinterSummaryDto(printer.getId(), printer.getName(), printer.getType(), connected);
                })
                .toList();
    }

    /**
     * Printers PrinterBridge currently knows about (story 3.11, AC1) — powers the admin
     * registration form's picker, replacing the manual serial-port/IP entry of story 3.8. Lets
     * {@link org.pluribourse.domain.print.exception.PrinterBridgeUnavailableException} propagate
     * uncaught — handled generically by {@code GlobalExceptionHandler} as a 503, distinct from an
     * empty result (PrinterBridge reachable, nothing detected). Already-registered printers are
     * excluded so the picker only ever offers printers that can actually be added.
     */
    public List<DiscoveredPrinterDto> discover() {
        Set<String> registeredPrinterBridgeIds = repository.findAllPrinterBridgeIds();
        return printerBridgeClient.discover().stream()
                .filter(printer -> !registeredPrinterBridgeIds.contains(printer.id()))
                .map(this::toDiscoveredPrinterDto)
                .toList();
    }

    private DiscoveredPrinterDto toDiscoveredPrinterDto(PrinterBridgeDiscoveredPrinter printer) {
        PrinterType type = printer.type() == PrinterBridgePrinterType.BLUETOOTH_THERMAL ? PrinterType.THERMAL : PrinterType.A4;
        return new DiscoveredPrinterDto(printer.id(), printer.name(), type, printer.status());
    }

    /**
     * Triggers an actual test print through PrinterBridge (story 3.11, AC5) — a stronger signal
     * than the connectivity check alone, since a dead Bluetooth link can still report as reachable
     * (see PrinterBridge's own dev notes).
     */
    public PrintResult testPrint(Long id) {
        Printer printer = repository.findById(id).orElseThrow(() -> new PrinterNotFoundException(id));
        return printerBridgeClient.testPrint(printer.getPrinterBridgeId());
    }

    /**
     * Deletes the persisted printer first, then tears down its queue (story 3.8, AC4) — in that
     * order, so a failed deletion never destroys a queue that is still valid.
     */
    @Transactional
    public void delete(Long id) {
        Printer printer = repository.findById(id).orElseThrow(() -> new PrinterNotFoundException(id));
        repository.delete(printer);
        printQueueService.unregisterPrinter(id);
    }

    // printerBridgeId is unconditionally required for every type, so it is enforced declaratively
    // via @NotBlank on CreatePrinterDto (framework-level 400) — unlike widthMm below, which is
    // only required for THERMAL and therefore can't be expressed as a plain Bean Validation
    // annotation, hence the manual check here (422, story-specific business rule).
    private void validateConfiguration(CreatePrinterDto dto) {
        if (dto.type() == PrinterType.THERMAL) {
            if (dto.widthMm() == null) {
                throw new InvalidPrinterConfigurationException("A THERMAL printer requires widthMm.");
            }
            if (dto.widthMm() != THERMAL_WIDTH_57 && dto.widthMm() != THERMAL_WIDTH_80) {
                throw new InvalidPrinterConfigurationException("widthMm must be 57 or 80 for a THERMAL printer.");
            }
        }
    }
}
