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
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders the per-seller sales report ("bilan de vente") as a PDF (FR-050): sold items (name,
 * unit price) and unsold items (name, category, table number, and — for a lot only — its price)
 * in two separate sections, gross total, commission rate and net payout. A lot with at least one
 * sold member is treated as sold as a whole (Story 5.2 code review patch, 2026-08-14): it appears
 * on exactly one line, in exactly one section, its global price counted exactly once, regardless
 * of how many of its members are individually marked sold. Built with OpenPDF
 * ({@code org.openpdf.*} packages — see story 3.6 Dev Notes § OpenPDF for the
 * {@code com.lowagie}/groupId pitfalls). Deliberately not sharing its table/font helpers with
 * {@link InvoiceRenderer}/{@link DepositSlipRenderer}: all three classes are already
 * independently tested and reviewed, and this renderer's structure genuinely differs (two tables
 * instead of one, an unsold section that doesn't exist elsewhere).
 */
@Component
@RequiredArgsConstructor
public class SettlementReportRenderer {

    private static final Font TITLE_FONT;
    private static final Font SECTION_FONT;
    private static final Font HEADER_FONT;
    private static final Font BODY_FONT;
    private static final Font TOTAL_FONT;

    static {
        try {
            // Explicit CP1252 base fonts, not embedded: the no-BaseFont Font(family, size, style)
            // constructor silently falls back to an embedded Unicode CID font as soon as the
            // Euro sign is rendered, which then encodes text as glyph indices instead of
            // characters — unreadable by anything expecting plain WinAnsi/Latin bytes.
            BaseFont helvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            BaseFont helveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            TITLE_FONT = new Font(helveticaBold, 16);
            SECTION_FONT = new Font(helveticaBold, 13);
            HEADER_FONT = new Font(helveticaBold, 11);
            BODY_FONT = new Font(helvetica, 11);
            TOTAL_FONT = new Font(helveticaBold, 12);
        } catch (DocumentException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MessageSource messageSource;

    public byte[] renderReport(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale, BigDecimal amountPaid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            // Uncompressed content stream: keeps the rendered text greppable as plain bytes for
            // integration tests, with no functional impact on a single-page A4 document.
            writer.setCompressionLevel(PdfStream.NO_COMPRESSION);
            document.open();

            String currency = sellerProfile.getEdition().getCurrency();
            document.add(new Paragraph(messageSource.getMessage("print.settlementReport.title", null, documentLocale), TITLE_FONT));
            document.add(new Paragraph(sellerProfile.getFirstName() + " " + sellerProfile.getLastName(), BODY_FONT));
            document.add(new Paragraph(sellerProfile.getEdition().getName(), BODY_FONT));
            document.add(new Paragraph(" "));

            // A lot with at least one sold member counts as sold for this report (Story 5.2 code
            // review patch, 2026-08-14): every member of such a lot is routed to the sold section
            // together, regardless of its own isSold() — otherwise a partially-sold lot would
            // appear on two lines (one per section) and its full price would be double-counted
            // relative to the gross total below, since ItemPricing.computeTotal already counts a
            // lot's global price once per distinct lot present in whichever list it is fed.
            Set<Long> soldLotIds = new HashSet<>();
            for (Item item : items) {
                if (item.isSold() && item.getLot() != null) {
                    soldLotIds.add(item.getLot().getId());
                }
            }
            List<Item> soldItems = items.stream()
                    .filter(item -> item.isSold() || (item.getLot() != null && soldLotIds.contains(item.getLot().getId())))
                    .toList();
            List<Item> unsoldItems = items.stream()
                    .filter(item -> !item.isSold() && (item.getLot() == null || !soldLotIds.contains(item.getLot().getId())))
                    .toList();

            document.add(new Paragraph(messageSource.getMessage("print.settlementReport.soldSection", null, documentLocale), SECTION_FONT));
            document.add(buildSoldItemsTable(soldItems, documentLocale, currency));
            document.add(new Paragraph(" "));

            document.add(new Paragraph(messageSource.getMessage("print.settlementReport.unsoldSection", null, documentLocale), SECTION_FONT));
            document.add(buildUnsoldItemsTable(unsoldItems, documentLocale, currency));
            document.add(new Paragraph(" "));

            BigDecimal total = ItemPricing.computeTotal(soldItems).setScale(2, RoundingMode.HALF_UP);
            BigDecimal commissionAmount = ItemPricing.computeCommission(total, commissionRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = ItemPricing.computeNetPayout(total, commissionRate);
            document.add(new Paragraph(
                    messageSource.getMessage("print.settlementReport.totalGross", new Object[]{total.toPlainString(), currency}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.settlementReport.commission", new Object[]{commissionRate.toPlainString()}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.settlementReport.commissionAmount", new Object[]{commissionAmount.toPlainString(), currency}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.settlementReport.netAmount", new Object[]{net.toPlainString(), currency}, documentLocale), TOTAL_FONT));
            // amountPaid is only known once the seller has actually been settled (Settlement.amount,
            // SETTLED status) — the report stays printable before that (AC4, story 5.2), so this line
            // is simply omitted rather than printing a misleading 0€.
            if (amountPaid != null) {
                document.add(new Paragraph(
                        messageSource.getMessage("print.settlementReport.amountPaid", new Object[]{amountPaid.setScale(2, RoundingMode.HALF_UP).toPlainString(), currency}, documentLocale), TOTAL_FONT));
            }

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render settlement report PDF", e);
        }
        return out.toByteArray();
    }

    private PdfPTable buildSoldItemsTable(List<Item> soldItems, Locale documentLocale, String currency) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.item", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.price", null, documentLocale)));

        for (Item item : ItemPricing.distinctByLot(soldItems)) {
            String name = item.getLot() != null ? item.getLot().getName() : item.getName();
            BigDecimal price = item.getLot() != null ? item.getLot().getGlobalPrice() : item.getPrice();
            table.addCell(new PdfPCell(new Phrase(name, BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(price.setScale(2, RoundingMode.HALF_UP) + currency, BODY_FONT)));
        }
        return table;
    }

    private PdfPTable buildUnsoldItemsTable(List<Item> unsoldItems, Locale documentLocale, String currency) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.item", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.category", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.table", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.price", null, documentLocale)));

        for (Item item : ItemPricing.distinctByLot(unsoldItems)) {
            String name = item.getLot() != null ? item.getLot().getName() : item.getName();
            // A lot must show its price in this section too (AC 1), unlike a standalone unsold
            // item, which never has a price cell here.
            String price = item.getLot() != null ? item.getLot().getGlobalPrice().setScale(2, RoundingMode.HALF_UP) + currency : "";
            table.addCell(new PdfPCell(new Phrase(name, BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.getCategory().getName(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(String.valueOf(item.getTableNumber()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(price, BODY_FONT)));
        }
        return table;
    }

    private PdfPCell headerCell(String text) {
        return new PdfPCell(new Phrase(text, HEADER_FONT));
    }
}
