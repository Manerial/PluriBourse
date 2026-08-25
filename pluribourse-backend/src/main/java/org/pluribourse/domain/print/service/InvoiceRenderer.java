package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfStream;
import org.openpdf.text.pdf.PdfWriter;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.item.service.ItemPricing;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders the A4 buyer invoice as a PDF (FR-041): every standalone item on its own line, every lot
 * deduplicated to a single line regardless of member count, association name, edition name, sale
 * date/time and total. Built with OpenPDF ({@code org.openpdf.*} packages — see story 3.6 Dev Notes
 * § OpenPDF for the {@code com.lowagie}/groupId pitfalls). Deliberately not sharing its table/font
 * helpers with {@link DepositSlipRenderer}: both classes are already independently tested and
 * reviewed (story 3.6, done) — three short private methods duplicated once is a smaller risk than
 * modifying that stable file for this story.
 */
@Component
@RequiredArgsConstructor
public class InvoiceRenderer {

    private static final Font TITLE_FONT;
    private static final Font HEADER_FONT;
    private static final Font BODY_FONT;
    private static final Font TOTAL_FONT;

    // No precedent for locale-aware date/number formatting in this module (DepositSlipRenderer
    // never localized its amounts either, story 3.5/3.6 decision) — a simple fixed pattern is
    // consistent with that existing convention.
    private static final DateTimeFormatter SOLD_AT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    static {
        try {
            // Explicit CP1252 base fonts, not embedded: the no-BaseFont Font(family, size, style)
            // constructor silently falls back to an embedded Unicode CID font as soon as the
            // Euro sign is rendered, which then encodes text as glyph indices instead of
            // characters — unreadable by anything expecting plain WinAnsi/Latin bytes.
            BaseFont helvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont helveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            TITLE_FONT = new Font(helveticaBold, 16);
            HEADER_FONT = new Font(helveticaBold, 11);
            BODY_FONT = new Font(helvetica, 11);
            TOTAL_FONT = new Font(helveticaBold, 12);
        } catch (DocumentException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MessageSource messageSource;

    public byte[] renderInvoice(String associationName, String editionName, LocalDateTime soldAt,
            List<Item> items, Locale documentLocale, String currency) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            // Uncompressed content stream: keeps the rendered text greppable as plain bytes for
            // integration tests, with no functional impact on a single-page A4 document.
            writer.setCompressionLevel(PdfStream.NO_COMPRESSION);
            document.open();

            document.add(new Paragraph(messageSource.getMessage("print.invoice.title", null, documentLocale), TITLE_FONT));
            document.add(new Paragraph(associationName, BODY_FONT));
            document.add(new Paragraph(editionName, BODY_FONT));
            document.add(new Paragraph(soldAt.format(SOLD_AT_FORMATTER), BODY_FONT));
            document.add(new Paragraph(" "));
            document.add(buildItemsTable(items, documentLocale, currency));
            document.add(new Paragraph(" "));

            BigDecimal total = ItemPricing.computeTotal(items).setScale(2, RoundingMode.HALF_UP);
            document.add(new Paragraph(
                    messageSource.getMessage("print.invoice.total", new Object[]{total.toPlainString(), currency}, documentLocale), TOTAL_FONT));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render invoice PDF", e);
        }
        return out.toByteArray();
    }

    private PdfPTable buildItemsTable(List<Item> items, Locale documentLocale, String currency) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.invoice.column.item", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.invoice.column.price", null, documentLocale)));

        for (Item item : ItemPricing.distinctByLot(items)) {
            if (item.getLot() != null) {
                addRow(table, item.getLot().getName(), item.getLot().getGlobalPrice(), currency);
            } else {
                addRow(table, item.getName(), item.getPrice(), currency);
            }
        }
        return table;
    }

    private PdfPCell headerCell(String text) {
        return new PdfPCell(new Phrase(text, HEADER_FONT));
    }

    private void addRow(PdfPTable table, String name, BigDecimal price, String currency) {
        table.addCell(new PdfPCell(new Phrase(name, BODY_FONT)));
        table.addCell(new PdfPCell(new Phrase(price.setScale(2, RoundingMode.HALF_UP) + currency, BODY_FONT)));
    }
}
