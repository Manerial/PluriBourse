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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders the per-seller sales report ("bilan de vente") as a PDF (FR-050). Story 5.8 replaced the
 * separate "sold"/"unsold" sections with:
 * <ul>
 *   <li>a single <b>unified items table</b> (name, category, table, price, status) — a lot on one
 *       line (its global price once), a standalone item on its own line;</li>
 *   <li>a <b>lot details table</b> (one row per lot member, with that member's <i>real</i>
 *       {@code isSold()} status) — rendered only when the seller has at least one lot;</li>
 *   <li>a <b>count line</b> — {@code sold + unsold = deposited}, counted per physical {@code Item}
 *       ({@code isSold()} real, no lot normalization).</li>
 * </ul>
 * The gross total / commission / net payout are unchanged: still computed on the <i>normalized</i>
 * {@code soldItems} list (a lot with ≥1 sold member counts as fully sold, its price once — Story
 * 5.2 code review patch, 2026-08-14), which also drives the <i>status</i> of a lot line in the
 * unified table. "Montant remis" is printed only once the seller is settled ({@code amountPaid != null}).
 * Built with OpenPDF ({@code org.openpdf.*} packages — see story 3.6 Dev Notes § OpenPDF). Its
 * table/font helpers are deliberately not shared with {@link InvoiceRenderer}/{@link DepositSlipRenderer}.
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

            // A lot with at least one sold member counts as sold as a whole (Story 5.2 code review
            // patch, 2026-08-14). This normalization now drives two things only: the STATUS of a
            // lot's line in the unified items table, and the gross total below (ItemPricing.computeTotal
            // counts a lot's global price once per distinct lot in whichever list it is fed, so a
            // partially-sold lot must not also leak into a second list). The count line and the
            // lot-details table use the raw per-member isSold() instead — see further down.
            Set<Long> soldLotIds = new HashSet<>();
            for (Item item : items) {
                if (item.isSold() && item.getLot() != null) {
                    soldLotIds.add(item.getLot().getId());
                }
            }
            List<Item> soldItems = items.stream()
                    .filter(item -> item.isSold() || (item.getLot() != null && soldLotIds.contains(item.getLot().getId())))
                    .toList();

            document.add(new Paragraph(messageSource.getMessage("print.settlementReport.itemsSection", null, documentLocale), SECTION_FONT));
            document.add(buildUnifiedItemsTable(items, soldLotIds, documentLocale, currency));
            document.add(new Paragraph(" "));

            if (items.stream().anyMatch(item -> item.getLot() != null)) {
                document.add(new Paragraph(messageSource.getMessage("print.settlementReport.lotDetailSection", null, documentLocale), SECTION_FONT));
                document.add(buildLotDetailTable(items, documentLocale));
                document.add(new Paragraph(" "));
            }

            // Count line: 1 physical Item = 1 unit, real isSold() per member (no distinctByLot, no
            // "lot with ≥1 sold member = fully sold" normalization — that stays for the totals only).
            long soldCount = items.stream().filter(Item::isSold).count();
            document.add(new Paragraph(messageSource.getMessage("print.settlementReport.countLine",
                    new Object[]{String.valueOf(soldCount), String.valueOf(items.size() - soldCount), String.valueOf(items.size())},
                    documentLocale), BODY_FONT));
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

    /**
     * One line per distinct lot or standalone item. A lot's status comes from {@code soldLotIds}
     * (the "≥1 member sold ⇒ sold" normalization), NEVER {@code representative.isSold()}:
     * {@code distinctByLot} keeps the first member encountered, which for a partially-sold lot may
     * be an unsold one. Its category / table are shared by every member since story 3.14, so the
     * representative's are correct.
     */
    private PdfPTable buildUnifiedItemsTable(List<Item> items, Set<Long> soldLotIds, Locale documentLocale, String currency) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.item", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.category", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.table", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.price", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.status", null, documentLocale)));

        for (Item representative : ItemPricing.distinctByLot(items)) {
            boolean isLot = representative.getLot() != null;
            String name = isLot ? representative.getLot().getName() : representative.getName();
            BigDecimal price = isLot ? representative.getLot().getGlobalPrice() : representative.getPrice();
            boolean sold = isLot ? soldLotIds.contains(representative.getLot().getId()) : representative.isSold();
            table.addCell(new PdfPCell(new Phrase(name, BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(representative.getCategory().getName(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(String.valueOf(representative.getTableNumber()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(price.setScale(2, RoundingMode.HALF_UP) + currency, BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(statusLabel(sold, documentLocale), BODY_FONT)));
        }
        return table;
    }

    /**
     * One row per lot member (not deduplicated), grouped by lot then by item number, each with its
     * OWN real {@code isSold()} status — so a partially-sold lot shows exactly which members went.
     * Rendered only when the seller has a lot. Category via {@code item.getCategory()} (fetch-joined,
     * = {@code Lot.category} since story 3.14), never the LAZY {@code item.getLot().getCategory()}.
     */
    private PdfPTable buildLotDetailTable(List<Item> items, Locale documentLocale) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.lot", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.item", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.category", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.table", null, documentLocale)));
        table.addCell(headerCell(messageSource.getMessage("print.settlementReport.column.status", null, documentLocale)));

        // Grouped by lot (then item number): items arrive in item-number order, which interleaves
        // members of lots that were registered in alternation.
        List<Item> lotMembers = items.stream()
                .filter(item -> item.getLot() != null)
                .sorted(Comparator.comparing((Item item) -> item.getLot().getId()).thenComparing(Item::getItemNumber))
                .toList();
        for (Item item : lotMembers) {
            table.addCell(new PdfPCell(new Phrase(item.getLot().getName(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.getName(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(item.getCategory().getName(), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(String.valueOf(item.getTableNumber()), BODY_FONT)));
            table.addCell(new PdfPCell(new Phrase(statusLabel(item.isSold(), documentLocale), BODY_FONT)));
        }
        return table;
    }

    private String statusLabel(boolean sold, Locale documentLocale) {
        return messageSource.getMessage(
                sold ? "print.settlementReport.status.sold" : "print.settlementReport.status.unsold", null, documentLocale);
    }

    private PdfPCell headerCell(String text) {
        return new PdfPCell(new Phrase(text, HEADER_FONT));
    }
}
