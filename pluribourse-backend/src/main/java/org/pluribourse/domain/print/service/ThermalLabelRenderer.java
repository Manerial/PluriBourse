package org.pluribourse.domain.print.service;

import com.google.zxing.common.*;
import com.google.zxing.oned.*;
import lombok.*;
import org.pluribourse.domain.item.entity.*;
import org.springframework.context.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

/**
 * Renders thermal roll content as raw ESC/POS bytes (FR-026/FR-027/FR-030/FR-032/FR-045). Text is
 * encoded as code page 858 (Cp858 — CP850 with the euro sign): on real hardware, commodity ESC/POS
 * printers default to CP437/CP850 (confirmed by the euro sign printing as "ñ", CP437/CP850's glyph
 * at the byte position ISO-8859-15 uses for €), not ISO-8859-15 as originally assumed. Cp858 is the
 * standard Epson ESC/POS table (index 19) for accented Latin characters plus the euro sign, so the
 * printer must be explicitly switched to it — INIT resets the printer to its power-on default table,
 * so SELECT_CODEPAGE_858 must follow every INIT.
 */
@Component
@RequiredArgsConstructor
public class ThermalLabelRenderer {

    private static final Charset LABEL_CHARSET = Charset.forName("Cp858");
    private static final byte[] INIT = {0x1B, 0x40}; // ESC @ — initialize printer
    private static final byte[] SELECT_CODEPAGE_858 = {0x1B, 0x74, 19}; // ESC t 19 — PC858 (Multilingual Latin I + Euro)
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
        writeBytes(out, SELECT_CODEPAGE_858);
        writeBytes(out, ALIGN_CENTER);
        writeLine(out, sellerFullName);
        writeLine(out, editionName);
        writeBytes(out, LINE_FEED);
        return out.toByteArray();
    }

    /**
     * A single article label — never mentions the seller's name (RGPD, FR-027).
     */
    public byte[] renderLabel(Item item, int printerWidthMm, Locale locale) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeBytes(out, INIT);
        writeBytes(out, SELECT_CODEPAGE_858);
        writeBytes(out, ALIGN_CENTER);

        writeLine(out, item.getEdition().getName());
        writeBytes(out, LINE_FEED);
        writeLine(out, "--- " + messageSource.getMessage("print.label.category", null, locale) + " ---");
        Lot lot = item.getLot();
        if (lot != null) {
            writeLine(out, item.getName());
            List<Item> lotItems = lot.getItems();
            String globalPrice = String.format(Locale.ROOT, "%.2f", lot.getGlobalPrice());
            writeLine(out, messageSource.getMessage("print.label.lotPrice", new Object[]{globalPrice}, locale));
            writeLine(out, messageSource.getMessage("print.label.lotIndivisible", new Object[]{String.valueOf(lotItems.indexOf(item) + 1), String.valueOf(lotItems.size())}, locale));
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

    /**
     * Separator printed between two consecutive article labels (FR-030) — a tear/cut point only, no text is specified anywhere in the source artifacts.
     */
    public byte[] articleSeparator() {
        return PARTIAL_CUT.clone();
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

    /**
     * GS v 0 — prints a 1-bit-per-pixel raster image built from the Code 128 barcode's bit matrix.
     */
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
