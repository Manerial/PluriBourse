package org.pluribourse.print.service;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import lombok.RequiredArgsConstructor;
import org.pluribourse.item.entity.Item;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Renders thermal roll content as raw ESC/POS bytes (FR-026/FR-027/FR-030/FR-032/FR-045). Text is
 * encoded as ISO-8859-15 (Latin-9): covers French/English accented characters and the euro sign
 * with a single-byte charset, which is what commodity ESC/POS thermal printers expect by default —
 * no printer-specific code-page selection command is sent (unverifiable without real hardware).
 */
@Component
@RequiredArgsConstructor
public class ThermalLabelRenderer {

    private static final Charset LABEL_CHARSET = Charset.forName("ISO-8859-15");
    private static final byte[] INIT = {0x1B, 0x40}; // ESC @ — initialize printer
    private static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01}; // ESC a 1 — center alignment
    private static final byte[] LINE_FEED = {0x0A};
    private static final byte[] PARTIAL_CUT = {0x1D, 0x56, 0x01}; // GS V 1 — partial cut

    private final MessageSource messageSource;

    /**
     * Vendor separator opening the roll (FR-030) — the only piece carrying the seller's name;
     * meant to be torn off by the volunteer, never attached to an article (RGPD, FR-027).
     */
    public byte[] renderSellerSeparator(String sellerFullName, String editionName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBytes(out, INIT);
        writeBytes(out, ALIGN_CENTER);
        writeLine(out, sellerFullName);
        writeLine(out, editionName);
        writeBytes(out, LINE_FEED);
        return out.toByteArray();
    }

    /**
     * A single article label — never mentions the seller's name (RGPD, FR-027).
     *
     * @param sellerItems all of the seller's currently-registered items, from the same snapshot
     *                    used to build the whole print job — used to compute a lot item's X/N
     *                    position (AC5) without a fresh DB query. Ignored for non-lot items.
     */
    public byte[] renderLabel(Item item, List<Item> sellerItems, int printerWidthMm, Locale locale) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBytes(out, INIT);
        writeBytes(out, ALIGN_CENTER);

        writeLine(out, item.getEdition().getName());
        writeBytes(out, LINE_FEED);
        writeLine(out, "--- " + messageSource.getMessage("print.label.category", null, locale) + " ---");

        if (item.getLot() != null) {
            writeLine(out, item.getName());
            String globalPrice = String.format(Locale.ROOT, "%.2f", item.getLot().getGlobalPrice());
            writeLine(out, messageSource.getMessage("print.label.lotPrice", new Object[]{globalPrice}, locale));
            int[] position = lotPosition(item, sellerItems);
            writeLine(out, messageSource.getMessage("print.label.lotIndivisible", new Object[]{String.valueOf(position[0]), String.valueOf(position[1])}, locale));
        } else {
            String price = String.format(Locale.ROOT, "%.2f", item.getPrice());
            writeLine(out, messageSource.getMessage("print.label.itemPrice", new Object[]{item.getName(), price}, locale));
        }

        if (item.isIncomplete()) {
            writeLine(out, messageSource.getMessage("print.label.incomplete", null, locale));
        }
        // Table number passed as a pre-formatted String, not the raw Integer: MessageFormat applies
        // locale-sensitive NumberFormat to bare {0} placeholders with Number arguments, which would
        // silently reformat the value (this bit us on the lot price above, formatted as BigDecimal).
        writeLine(out, messageSource.getMessage("print.label.table", new Object[]{String.valueOf(item.getTableNumber())}, locale));
        writeBytes(out, LINE_FEED);

        writeBarcode(out, item.getBarcode(), printAreaDots(printerWidthMm));
        writeLine(out, item.getFormattedBarcode());
        writeBytes(out, LINE_FEED);
        return out.toByteArray();
    }

    /** Separator printed between two consecutive article labels (FR-030) — a tear/cut point only, no text is specified anywhere in the source artifacts. */
    public byte[] articleSeparator() {
        return PARTIAL_CUT.clone();
    }

    /**
     * [X, N] — X = 1-based position of this item among its lot siblings in creation order, N =
     * sibling count (FR-045). Computed from the already-loaded {@code sellerItems} snapshot
     * (every lot sibling belongs to the same seller) rather than a fresh repository query: the
     * print job runs on the queue's consumer thread, detached from the original request, so
     * re-querying here would both cost one query per lot item and risk failing outright if a
     * sibling was deleted between deposit validation and the (possibly delayed) actual print.
     */
    private static int[] lotPosition(Item item, List<Item> sellerItems) {
        List<Item> siblings = sellerItems.stream()
                .filter(i -> item.getLot().getId().equals(i.getLot() != null ? i.getLot().getId() : null))
                .sorted(Comparator.comparing(Item::getId))
                .toList();
        int position = 1;
        for (Item sibling : siblings) {
            if (sibling.getId().equals(item.getId())) {
                break;
            }
            position++;
        }
        return new int[]{position, siblings.size()};
    }

    private static int printAreaDots(int printerWidthMm) {
        // Standard ESC/POS printable-width conventions at 203 DPI (~8 dots/mm): 58 mm rolls print
        // 384 dots wide (48 mm printable area), 80 mm rolls print 576 dots wide (72 mm printable
        // area). Approximation accepted for this story — no AC requires precise DPI calibration.
        return printerWidthMm >= 80 ? 576 : 384;
    }

    private void writeLine(ByteArrayOutputStream out, String text) {
        writeBytes(out, text.getBytes(LABEL_CHARSET));
        writeBytes(out, LINE_FEED);
    }

    private void writeBytes(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(bytes);
    }

    /** GS v 0 — prints a 1-bit-per-pixel raster image built from the Code 128 barcode's bit matrix. */
    private void writeBarcode(ByteArrayOutputStream out, String payload, int widthPx) {
        BitMatrix matrix = new Code128Writer().encode(payload, com.google.zxing.BarcodeFormat.CODE_128, widthPx, 80);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int bytesPerRow = (width + 7) / 8;

        writeBytes(out, new byte[]{0x1D, 0x76, 0x30, 0x00});
        writeBytes(out, new byte[]{(byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF)});
        writeBytes(out, new byte[]{(byte) (height & 0xFF), (byte) ((height >> 8) & 0xFF)});

        byte[] raster = new byte[bytesPerRow * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    int byteIndex = y * bytesPerRow + (x / 8);
                    raster[byteIndex] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        writeBytes(out, raster);
        writeBytes(out, LINE_FEED);
    }
}
