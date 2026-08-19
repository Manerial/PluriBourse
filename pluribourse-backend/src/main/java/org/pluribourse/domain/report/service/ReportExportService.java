package org.pluribourse.domain.report.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.edition.entity.Edition;
import org.pluribourse.domain.item.dto.ItemCatalogDto;
import org.pluribourse.domain.item.mapper.ItemMapper;
import org.pluribourse.domain.item.repository.ItemRepository;
import org.pluribourse.domain.item.service.PhaseGuard;
import org.pluribourse.domain.payout.dto.SettlementDto;
import org.pluribourse.domain.payout.service.SettlementService;
import org.pluribourse.domain.user.enums.Language;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Story 5.5 — CSV exports of the catalog and settlements for an edition (FR-091, FR-092), admin
 * only, reachable in Post-vente/Clôturée like {@link ReportService#getEditionReport}. Unlike the
 * PDF reports, this is a plain synchronous HTTP download — it never goes through
 * {@code PrintQueueService}/{@code PrinterBridgeClient}. Reuses {@link PhaseGuard#requirePostSaleOrClosedPhase}
 * (and the {@code EditionReportNotAllowedException} it throws) rather than introducing another
 * {@code *NotAllowedException} (already flagged as a pattern to not aggravate, story 5.4 review).
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;
    private final SettlementService settlementService;
    private final MessageSource messageSource;

    @Transactional(readOnly = true)
    public byte[] exportCatalogCsv(Edition edition) {
        PhaseGuard.requirePostSaleOrClosedPhase(edition);
        Locale documentLocale = resolveLocale(edition);

        List<ItemCatalogDto> items = itemMapper.toCatalogDtos(itemRepository.findAllByEditionIdForCatalog(edition.getId()));

        StringBuilder csv = new StringBuilder();
        writeRow(csv,
                message("export.catalog.column.name", documentLocale),
                message("export.catalog.column.barcode", documentLocale),
                message("export.catalog.column.category", documentLocale),
                message("export.catalog.column.table", documentLocale),
                message("export.catalog.column.price", documentLocale),
                message("export.catalog.column.completeness", documentLocale),
                message("export.catalog.column.soldStatus", documentLocale),
                message("export.catalog.column.seller", documentLocale));

        for (ItemCatalogDto item : items) {
            writeRow(csv,
                    item.name(),
                    item.barcode(),
                    item.categoryName(),
                    item.tableNumber() != null ? String.valueOf(item.tableNumber()) : "",
                    item.price() != null ? item.price().toPlainString() : "",
                    item.incomplete()
                            ? message("export.catalog.value.incomplete", documentLocale)
                            : message("export.catalog.value.complete", documentLocale),
                    item.sold()
                            ? message("export.catalog.value.sold", documentLocale)
                            : message("export.catalog.value.unsold", documentLocale),
                    item.sellerFirstName() + " " + item.sellerLastName());
        }

        return toBytes(csv);
    }

    @Transactional(readOnly = true)
    public byte[] exportSettlementsCsv(Edition edition) {
        PhaseGuard.requirePostSaleOrClosedPhase(edition);
        Locale documentLocale = resolveLocale(edition);

        List<SettlementDto> settlements = settlementService.getSettlementsForEdition(edition);

        StringBuilder csv = new StringBuilder();
        writeRow(csv,
                message("export.settlement.column.lastName", documentLocale),
                message("export.settlement.column.firstName", documentLocale),
                message("export.settlement.column.phone", documentLocale),
                message("export.settlement.column.email", documentLocale),
                message("export.settlement.column.amountDue", documentLocale),
                message("export.settlement.column.status", documentLocale));

        for (SettlementDto settlement : settlements) {
            writeRow(csv,
                    settlement.lastName(),
                    settlement.firstName(),
                    settlement.phone(),
                    settlement.email(),
                    settlement.amountDue().toPlainString(),
                    message("export.settlement.status." + settlement.status().name().toLowerCase(Locale.ROOT), documentLocale));
        }

        return toBytes(csv);
    }

    private Locale resolveLocale(Edition edition) {
        return edition.getDocumentLanguage() == Language.FR ? Locale.FRENCH : Locale.ENGLISH;
    }

    private String message(String code, Locale locale) {
        return messageSource.getMessage(code, null, locale);
    }

    private void writeRow(StringBuilder csv, String... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(escape(fields[i]));
        }
        csv.append("\r\n");
    }

    // RFC 4180: every field is wrapped in double quotes, and any internal double quote is doubled
    // — a seller comment or item name can contain a comma or a quote. A leading apostrophe also
    // neutralizes CSV formula injection in Excel/LibreOffice: a name starting with =, +, - or @
    // would otherwise execute as a formula when the export is opened (story 5.5 review).
    private String escape(String field) {
        String value = field != null ? field : "";
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    // The BOM is required for Excel (the realistic target for volunteers/the association) to
    // interpret accented characters (é, è, à) as UTF-8 instead of the system codepage.
    private byte[] toBytes(StringBuilder csv) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(UTF8_BOM);
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
