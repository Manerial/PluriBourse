package org.pluribourse.domain.print.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.pluribourse.domain.print.dto.CreatePrinterDto;
import org.pluribourse.domain.print.dto.PrinterDto;
import org.pluribourse.domain.print.entity.Printer;

@Mapper(componentModel = "spring")
public interface PrinterMapper {

    PrinterDto toDto(Printer printer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    Printer toEntity(CreatePrinterDto dto);
}
