export interface PrinterSummary {
  id: number;
  name: string;
  type: 'THERMAL' | 'A4';
  connected: boolean;
  pendingVerification: boolean;
}

export interface DiscoveredPrinter {
  printerBridgeId: string;
  name: string;
  type: 'THERMAL' | 'A4';
  status: 'ONLINE' | 'OFFLINE' | 'UNKNOWN';
}

export interface CreatePrinterPayload {
  name: string;
  type: 'THERMAL' | 'A4';
  widthMm: number | null;
  printerBridgeId: string;
  status: 'ONLINE' | 'OFFLINE' | 'UNKNOWN';
}

export interface PrintResult {
  status: 'OK' | 'ERROR';
  message: string | null;
}

export interface IgnoredPrinter {
  printerBridgeId: string;
  name: string | null;
  /** ISO-8601 date (yyyy-MM-dd), serialized from the backend's {@code LocalDate}. */
  ignoredAt: string;
}
