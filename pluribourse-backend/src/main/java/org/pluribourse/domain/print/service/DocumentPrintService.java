package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.print.entity.PrintContentType;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Builds the single {@link PrintJob} that sends the rendered deposit slip PDF (FR-031) to an A4
 * printer. Since story 3.12, the PDF bytes are sent to PrinterBridge over WebSocket instead of a
 * raw TCP socket — {@link PrinterBridgeClient#print} owns the timeout, no bounded-executor wrapper
 * needed here anymore.
 */
@Component
@RequiredArgsConstructor
public class DocumentPrintService {

    private final DepositSlipRenderer renderer;
    private final PrinterBridgeClient printerBridgeClient;

    public PrintJob buildDepositSlipJob(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        return printer -> print(printer.getPrinterBridgeId(), sellerProfile, items, commissionRate, documentLocale);
    }

    private void print(String printerBridgeId, SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate,
            Locale documentLocale) {
        byte[] pdf = renderer.renderSlip(sellerProfile, items, commissionRate, documentLocale);
        printerBridgeClient.print(printerBridgeId, PrintContentType.PDF, pdf);
    }
}
