import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreatePrinterPayload, DiscoveredPrinter, IgnoredPrinter, PrintResult, PrinterSummary } from '../models/printer-registry.model';

@Injectable({ providedIn: 'root' })
export class PrinterRegistryService {
  private readonly http = inject(HttpClient);

  list(): Observable<PrinterSummary[]> {
    return this.http.get<PrinterSummary[]>('/api/admin/printers');
  }

  discover(): Observable<DiscoveredPrinter[]> {
    return this.http.get<DiscoveredPrinter[]>('/api/admin/printers/discovered');
  }

  ignore(printerBridgeId: string): Observable<void> {
    return this.http.post<void>(`/api/admin/printers/discovered/${printerBridgeId}/ignore`, {});
  }

  listIgnored(): Observable<IgnoredPrinter[]> {
    return this.http.get<IgnoredPrinter[]>('/api/admin/printers/ignored');
  }

  reactivate(printerBridgeId: string): Observable<void> {
    return this.http.delete<void>(`/api/admin/printers/ignored/${printerBridgeId}`);
  }

  create(payload: CreatePrinterPayload): Observable<void> {
    return this.http.post<void>('/api/admin/printers', payload);
  }

  testPrint(id: number): Observable<PrintResult> {
    return this.http.post<PrintResult>(`/api/admin/printers/${id}/test-print`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/printers/${id}`);
  }
}
