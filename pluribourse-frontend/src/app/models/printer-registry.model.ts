export interface PrinterSummary {
  id: number;
  name: string;
  type: 'THERMAL' | 'A4';
  connected: boolean;
}

export interface SerialPortOption {
  systemPortName: string;
  descriptiveName: string;
}

export interface CreatePrinterPayload {
  name: string;
  type: 'THERMAL' | 'A4';
  serialPort: string | null;
  widthMm: number | null;
  host: string | null;
  port: number | null;
}
