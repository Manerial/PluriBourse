package org.pluribourse.domain.print.service;

import lombok.RequiredArgsConstructor;
import org.pluribourse.domain.item.entity.Item;
import org.pluribourse.domain.print.entity.PrintContentType;
import org.pluribourse.domain.seller.entity.SellerProfile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Builds the {@link PrintJob}s that send rendered A4 PDFs (deposit slip FR-031, buyer invoice
 * FR-041) to an A4 printer. Since story 3.12, the PDF bytes are sent to PrinterBridge over
 * WebSocket instead of a raw TCP socket — {@link PrinterBridgeClient#print} owns the timeout, no
 * bounded-executor wrapper needed here anymore.
 */
@Component
@RequiredArgsConstructor
public class DocumentPrintService {

    private final DepositSlipRenderer depositSlipRenderer;
    private final InvoiceRenderer invoiceRenderer;
    private final PrinterBridgeClient printerBridgeClient;

    public PrintJob buildDepositSlipJob(SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate, Locale documentLocale) {
        return printer -> printDepositSlip(printer.getPrinterBridgeId(), sellerProfile, items, commissionRate, documentLocale);
    }

    public PrintJob buildInvoiceJob(String associationName, String editionName, LocalDateTime soldAt, List<Item> items,
            Locale documentLocale) {
        return printer -> printInvoice(printer.getPrinterBridgeId(), associationName, editionName, soldAt, items, documentLocale);
    }

    private void printDepositSlip(String printerBridgeId, SellerProfile sellerProfile, List<Item> items, BigDecimal commissionRate,
            Locale documentLocale) {
        byte[] pdf = depositSlipRenderer.renderSlip(sellerProfile, items, commissionRate, documentLocale);
        printPdf(printerBridgeId, pdf);
    }

    private void printInvoice(String printerBridgeId, String associationName, String editionName, LocalDateTime soldAt,
            List<Item> items, Locale documentLocale) {
        byte[] pdf = invoiceRenderer.renderInvoice(associationName, editionName, soldAt, items, documentLocale);
        printPdf(printerBridgeId, pdf);
    }

    private void printPdf(String printerBridgeId, byte[] pdf) {
        printerBridgeClient.print(printerBridgeId, PrintContentType.PDF, pdf);
    }
}
