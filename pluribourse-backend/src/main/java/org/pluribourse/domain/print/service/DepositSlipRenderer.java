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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Renders the A4 deposit slip as a PDF (FR-031): every standalone item on its own line, every lot
 * deduplicated to a single line regardless of member count, commission rate and expected net
 * payout. When the seller has at least one lot, a second "lot details" table lists every member
 * item (lot name, lot category, item name — no price) so the seller knows which physical articles
 * make up each lot (story 5.8, FR-031). Built with OpenPDF ({@code org.openpdf.*} packages — see
 * story 3.6 Dev Notes § OpenPDF for the {@code com.lowagie}/groupId pitfalls).
 */
@Component
@RequiredArgsConstructor
public class DepositSlipRenderer {

    private static final Font TITLE_FONT;
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
            HEADER_FONT = new Font(helveticaBold, 11);
            BODY_FONT = new Font(helvetica, 11);
            TOTAL_FONT = new Font(helveticaBold, 12);
        } catch (DocumentException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MessageSource messageSource;

    public byte[] renderSlip(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter writer = PdfWriter.getInstance(document, out);
            // Uncompressed content stream: keeps the rendered text greppable as plain bytes for
            // integration tests, with no functional impact on a single-page A4 document.
            writer.setCompressionLevel(PdfStream.NO_COMPRESSION);
            document.open();

            String currency = sellerProfile.getEdition().getCurrency();
            document.add(new Paragraph(messageSource.getMessage("print.slip.title", null, documentLocale), TITLE_FONT));
            document.add(new Paragraph(sellerProfile.getFirstName() + " " + sellerProfile.getLastName(), BODY_FONT));
            document.add(new Paragraph(sellerProfile.getEdition().getName(), BODY_FONT));
            document.add(new Paragraph(" "));
            document.add(buildItemsTable(items, documentLocale, currency));
            document.add(new Paragraph(" "));

            if (items.stream().anyMatch(item -> item.getLot() != null)) {
                document.add(new Paragraph(messageSource.getMessage("print.slip.lotDetailSection", null, documentLocale), HEADER_FONT));
                document.add(buildLotDetailTable(items, documentLocale));
                document.add(new Paragraph(" "));
            }

            BigDecimal total = ItemPricing.computeTotal(items).setScale(2, RoundingMode.HALF_UP);
            BigDecimal net = ItemPricing.computeNetPayout(total, commissionRate);
            document.add(new Paragraph(
                    messageSource.getMessage("print.slip.totalGross", new Object[]{total.toPlainString(), currency}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.slip.commission", new Object[]{commissionRate.toPlainString()}, documentLocale), BODY_FONT));
            document.add(new Paragraph(
                    messageSource.getMessage("print.slip.netAmount", new Object[]{net.toPlainString(), currency}, documentLocale), TOTAL_FONT));

            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to render deposit slip PDF", e);
        }
        return out.toByteArray();
    }

    private PdfPTable buildItemsTable(List<Item> items, Locale documentLocale, String currency) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.slip.column.item", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.slip.column.price", null, documentLocale)));

        for (Item item : ItemPricing.distinctByLot(items)) {
            if (item.getLot() != null) {
                addRow(table, item.getLot().getName(), item.getLot().getGlobalPrice(), currency);
            } else {
                addRow(table, item.getName(), item.getPrice(), currency);
            }
        }
        return table;
    }

    /**
     * One row per lot member (not deduplicated by lot, unlike {@link #buildItemsTable}), grouped by
     * lot then by item number, so the seller sees every physical article of a lot listed together.
     * No price column — lot members have no individual price. The lot category comes from
     * {@code item.getCategory()} (already {@code JOIN FETCH}ed, and equal to {@code Lot.category} for
     * a member since story 3.14) — never {@code item.getLot().getCategory()}, which is LAZY, not
     * fetch-joined, and would throw a LazyInitializationException on the print queue's consumer
     * thread after the transaction closes.
     */
    private PdfPTable buildLotDetailTable(List<Item> items, Locale documentLocale) {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.slip.column.lot", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.slip.column.lotCategory", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.slip.column.lotItem", null, documentLocale)));

        // Grouped by lot (then item number): items arrive in item-number order, which interleaves
        // members of lots that were registered in alternation.
        List<Item> lotMembers = items.stream()
                .filter(item -> item.getLot() != null)
                .sorted(Comparator.comparing((Item item) -> item.getLot().getId()).thenComparing(Item::getItemNumber))
                .toList();
        for (Item item : lotMembers) {
            table.addCell(new PdfPCell(new Phrase(item.getLot().getName(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.getCategory().getName(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.getName(), BODY_FONT)));
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
