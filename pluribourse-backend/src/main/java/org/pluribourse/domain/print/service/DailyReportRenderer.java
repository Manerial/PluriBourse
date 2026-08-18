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
import org.pluribourse.domain.report.dto.DailySalesReportDto;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Renders the daily sales report ("bilan des ventes journalier") as a PDF (FR-054, FR-094): items
 * sold today / unsold, gross revenue, commission earned and a payment-method breakdown. Simpler
 * than {@link SettlementReportRenderer}: no item listing table — FR-054 asks for counts and
 * amounts, not a line-by-line itemization. Built with OpenPDF ({@code org.openpdf.*} packages —
 * see story 3.6 Dev Notes § OpenPDF for the {@code com.lowagie}/groupId pitfalls).
 */
@Component
@RequiredArgsConstructor
public class DailyReportRenderer {

    private static final Font TITLE_FONT;
    private static final Font HEADER_FONT;
    private static final Font BODY_FONT;

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
        } catch (DocumentException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MessageSource messageSource;

    public byte[] renderDailyReport(String editionName, DailySalesReportDto report, Locale documentLocale) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            // Uncompressed content stream: keeps the rendered text greppable as plain bytes for
            // integration tests, with no functional impact on a single-page A4 document.
            writer.setCompressionLevel(PdfStream.NO_COMPRESSION);
            document.open();

            document.add(new Paragraph(messageSource.getMessage("print.dailyReport.title", null, documentLocale), TITLE_FONT));
            document.add(new Paragraph(editionName, BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.dailyReport.date", new Object[]{report.reportDate().toString()}, documentLocale), BODY_FONT));
            document.add(new Paragraph(" "));

            document.add(new Paragraph(
                    messageSource.getMessage("print.dailyReport.soldCount", new Object[]{String.valueOf(report.soldItemCount())}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.dailyReport.unsoldCount", new Object[]{String.valueOf(report.unsoldItemCount())}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.dailyReport.grossRevenue",
                            new Object[]{report.grossRevenue().setScale(2, RoundingMode.HALF_UP).toPlainString()}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.dailyReport.commission",
                            new Object[]{report.commission().setScale(2, RoundingMode.HALF_UP).toPlainString()}, documentLocale), BODY_FONT));
            document.add(new Paragraph(" "));

            document.add(new Paragraph(messageSource.getMessage("print.dailyReport.paymentBreakdown", null, documentLocale), HEADER_FONT));
            document.add(buildPaymentBreakdownTable(report, documentLocale));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render daily sales report PDF", e);
        }
        return out.toByteArray();
    }

    private PdfPTable buildPaymentBreakdownTable(DailySalesReportDto report, Locale documentLocale) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.dailyReport.column.method", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.dailyReport.column.amount", null, documentLocale)));

        addPaymentRow(table, messageSource.getMessage("print.dailyReport.method.cash", null, documentLocale), report.cashTotal(), documentLocale);
        addPaymentRow(table, messageSource.getMessage("print.dailyReport.method.check", null, documentLocale), report.checkTotal(), documentLocale);
        addPaymentRow(table, messageSource.getMessage("print.dailyReport.method.card", null, documentLocale), report.cardTotal(), documentLocale);
        return table;
    }

    private void addPaymentRow(PdfPTable table, String label, BigDecimal amount, Locale documentLocale) {
        table.addCell(new PdfPCell(new Phrase(label, BODY_FONT)));
        String formattedAmount = messageSource.getMessage("print.dailyReport.amountFormat",
                new Object[]{amount.setScale(2, RoundingMode.HALF_UP).toPlainString()}, documentLocale);
        table.addCell(new PdfPCell(new Phrase(formattedAmount, BODY_FONT)));
    }

    private PdfPCell headerCell(String text) {
        return new PdfPCell(new Phrase(text, HEADER_FONT));
    }
}
