package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterType;
import org.pluribourse.domain.print.exception.InvalidPrinterConfigurationException;
import org.pluribourse.domain.print.mapper.PrinterMapper;
import org.pluribourse.domain.print.repository.PrinterRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
