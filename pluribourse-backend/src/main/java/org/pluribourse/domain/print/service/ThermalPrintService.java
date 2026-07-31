package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.print.entity.PrintContentType;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

/**
 * Builds the single {@link PrintJob} submitted when a seller's labels are (re)printed (FR-028):
 * vendor separator followed by every article label, article separators in between. Since story 3.12, the
 * full content is assembled in memory and sent to PrinterBridge as one WebSocket binary frame —
 * its protocol expects one control message (declaring the total size) followed by one payload,
 * not a stream of small writes like the serial port this class used to write to directly.
 * {@link PrinterBridgeClient#print} owns the timeout for the whole operation; this class no
 * longer needs its own bounded-executor wrapper.
 */
@Component
@RequiredArgsConstructor
public class ThermalPrintService {

    private final ThermalLabelRenderer renderer;
    private final PrinterBridgeClient printerBridgeClient;

    public PrintJob buildDepositJob(SellerProfile sellerProfile, List<Item> items, Locale documentLocale) {
        String sellerFullName = sellerProfile.getFirstName() + " " + sellerProfile.getLastName();
        String editionName = sellerProfile.getEdition().getName();

        return printer -> print(printer.getPrinterBridgeId(), sellerFullName, editionName, items, printer.getWidthMm(), documentLocale);
    }

    private void print(String printerBridgeId, String sellerFullName, String editionName, List<Item> items,
            int printerWidthMm, Locale documentLocale) {
        byte[] payload = buildPayload(sellerFullName, editionName, items, printerWidthMm, documentLocale);
        printerBridgeClient.print(printerBridgeId, PrintContentType.ESC_POS, payload);
    }

    private byte[] buildPayload(String sellerFullName, String editionName, List<Item> items, int printerWidthMm,
            Locale documentLocale) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            baos.write(renderer.renderSellerSeparator(sellerFullName, editionName));
            for (int i = 0; i < items.size(); i++) {
                baos.write(renderer.renderLabel(items.get(i), items, printerWidthMm, documentLocale));
                if (i < items.size() - 1) {
                    baos.write(renderer.articleSeparator());
                }
            }
        } catch (IOException e) {
            // ByteArrayOutputStream never actually throws — kept to satisfy OutputStream's checked
            // signature, mirrors the same pattern used everywhere else content is rendered in this module.
            throw new UncheckedIOException(e);
        }
        return baos.toByteArray();
    }
}
