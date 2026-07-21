export interface PrinterStatus {
  id: number;
  name: string;
  type: 'THERMAL' | 'A4';
  connected: boolean;
  queueDepth: number;
  jobInProgress: boolean;
  lastError: string | null;
  canRetry: boolean;
}
