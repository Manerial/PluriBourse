package org.pluribourse.domain.print.service;

import com.fazecast.jSerialComm.SerialPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.dto.PrinterSummaryDto;
import org.pluribourse.domain.print.dto.SerialPortDto;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterConfigurationException;
import org.pluribourse.domain.print.exception.PrinterNotFoundException;
import org.pluribourse.domain.print.mapper.PrinterMapper;
import org.pluribourse.domain.print.repository.PrinterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrinterService {

    private static final int DEFAULT_A4_PORT = 9100;
    private static final int THERMAL_WIDTH_57 = 57;
    private static final int THERMAL_WIDTH_80 = 80;

    private final PrinterRepository repository;
    private final PrinterMapper mapper;
    private final PrintQueueService printQueueService;

    @Transactional
    public PrinterDto create(CreatePrinterDto dto) {
        validateConfiguration(dto);
        Printer printer = mapper.toEntity(dto);
        if (printer.getType() == PrinterType.A4 && printer.getPort() == null) {
            printer.setPort(DEFAULT_A4_PORT);
        }
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
     * Enumerates serial ports currently visible to the JVM (story 3.8, AC3) — a simple
     * enumeration, not a potentially blocking I/O call like {@code openPort()}, so no timeout
     * wrapper is needed here (unlike {@link SerialPrinterConnectivityChecker}). Falls back to an
     * empty list if the native jSerialComm library fails to load or throws (e.g. no serial
     * subsystem available in a container) — the admin form already treats an empty list as "no
     * port detected" (code review finding, story 3.8).
     */
    public List<SerialPortDto> listAvailableSerialPorts() {
        try {
            return Arrays.stream(SerialPort.getCommPorts())
                    .map(port -> new SerialPortDto(port.getSystemPortName(), port.getDescriptivePortName()))
                    .toList();
        } catch (RuntimeException | LinkageError e) {
            log.warn("Failed to enumerate serial ports: {}", e.getMessage());
            return List.of();
        }
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

    private void validateConfiguration(CreatePrinterDto dto) {
        if (dto.type() == PrinterType.THERMAL) {
            if (!StringUtils.hasText(dto.serialPort()) || dto.widthMm() == null) {
                throw new InvalidPrinterConfigurationException(
                        "A THERMAL printer requires serialPort and widthMm.");
            }
            if (dto.widthMm() != THERMAL_WIDTH_57 && dto.widthMm() != THERMAL_WIDTH_80) {
                throw new InvalidPrinterConfigurationException(
                        "widthMm must be 57 or 80 for a THERMAL printer.");
            }
        } else if (!StringUtils.hasText(dto.host())) {
            throw new InvalidPrinterConfigurationException("An A4 printer requires a host.");
        }
    }
}
