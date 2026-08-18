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
import org.pluribourse.domain.report.dto.EditionSummaryReportDto;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Renders the edition-wide summary report ("bilan d'édition") as a PDF (FR-055, FR-094): items
 * sold/unsold over the edition's whole lifetime, gross revenue, commission earned and a
 * payment-method breakdown. Same pattern as {@link DailyReportRenderer} (story 5.3), minus the
 * "Date" line — this report is not scoped to a single day.
 */
@Component
@RequiredArgsConstructor
public class EditionReportRenderer {

    private static final Font TITLE_FONT;
    private static final Font HEADER_FONT;
    private static final Font BODY_FONT;

    static {
        try {
            // Explicit CP1252 base fonts, not embedded — see DailyReportRenderer for the Euro-sign
            // fallback-to-CID-font pitfall this avoids.
            BaseFont helvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont helveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            TITLE_FONT = new Font(helveticaBold, 16);
            HEADER_FONT = new Font(helveticaBold, 11);
            BODY_FONT = new Font(helvetica, 11);
        } catch (DocumentException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MessageSource messageSource;

    public byte[] renderEditionReport(String editionName, EditionSummaryReportDto report, Locale documentLocale) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            // Uncompressed content stream: keeps the rendered text greppable as plain bytes for
            // integration tests, with no functional impact on a single-page A4 document.
            writer.setCompressionLevel(PdfStream.NO_COMPRESSION);
            document.open();

            document.add(new Paragraph(messageSource.getMessage("print.editionReport.title", null, documentLocale), TITLE_FONT));
            document.add(new Paragraph(editionName, BODY_FONT));
            document.add(new Paragraph(" "));

            document.add(new Paragraph(
                    messageSource.getMessage("print.editionReport.soldCount", new Object[]{String.valueOf(report.soldItemCount())}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.editionReport.unsoldCount", new Object[]{String.valueOf(report.unsoldItemCount())}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.editionReport.grossRevenue",
                            new Object[]{report.grossRevenue().setScale(2, RoundingMode.HALF_UP).toPlainString()}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.editionReport.commission",
                            new Object[]{report.commission().setScale(2, RoundingMode.HALF_UP).toPlainString()}, documentLocale), BODY_FONT));
            document.add(new Paragraph(" "));

            document.add(new Paragraph(messageSource.getMessage("print.editionReport.paymentBreakdown", null, documentLocale), HEADER_FONT));
            document.add(buildPaymentBreakdownTable(report, documentLocale));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render edition summary report PDF", e);
        }
        return out.toByteArray();
    }

    private PdfPTable buildPaymentBreakdownTable(EditionSummaryReportDto report, Locale documentLocale) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.editionReport.column.method", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.editionReport.column.amount", null, documentLocale)));

        addPaymentRow(table, messageSource.getMessage("print.editionReport.method.cash", null, documentLocale), report.cashTotal(), documentLocale);
        addPaymentRow(table, messageSource.getMessage("print.editionReport.method.check", null, documentLocale), report.checkTotal(), documentLocale);
        addPaymentRow(table, messageSource.getMessage("print.editionReport.method.card", null, documentLocale), report.cardTotal(), documentLocale);
        return table;
    }

    private void addPaymentRow(PdfPTable table, String label, BigDecimal amount, Locale documentLocale) {
        table.addCell(new PdfPCell(new Phrase(label, BODY_FONT)));
        String formattedAmount = messageSource.getMessage("print.editionReport.amountFormat",
                new Object[]{amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}, documentLocale);
        table.addCell(new PdfPCell(new Phrase(formattedAmount, BODY_FONT)));
    }

    private PdfPCell headerCell(String text) {
        return new PdfPCell(new Phrase(text, HEADER_FONT));
    }
}
