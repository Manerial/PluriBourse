package org.pluribourse.print.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.pluribourse.print.dto.CreatePrinterDto;
import org.pluribourse.print.dto.PrinterDto;
import org.pluribourse.print.entity.Printer;

@Mapper(componentModel = "spring")
public interface PrinterMapper {

    PrinterDto toDto(Printer printer);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    Printer toEntity(CreatePrinterDto dto);
}
