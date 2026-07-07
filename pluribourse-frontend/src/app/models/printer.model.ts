export interface AvailablePrinter {
  id: number;
  name: string;
  type: 'THERMAL' | 'A4';
}

export interface PrinterSelectionStatus {
  done: boolean;
  thermalPrinterId: number | null;
  a4PrinterId: number | null;
}
