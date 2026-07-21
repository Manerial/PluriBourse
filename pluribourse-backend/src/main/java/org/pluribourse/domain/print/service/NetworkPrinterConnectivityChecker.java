package org.pluribourse.domain.print.service;

import org.pluribourse.domain.print.entity.Printer;
import org.pluribourse.domain.print.entity.PrinterType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

@Component
public class NetworkPrinterConnectivityChecker implements PrinterConnectivityChecker {

    private static final int CONNECT_TIMEOUT_MS = 2000;

    @Override
    public PrinterType getSupportedType() {
        return PrinterType.A4;
    }

    @Override
    public void checkAccessibility(Printer printer) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(printer.getHost(), printer.getPort()), CONNECT_TIMEOUT_MS);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot connect to " + printer.getHost() + ":" + printer.getPort(), e);
        }
    }
}
