import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreatePrinterPayload, DiscoveredPrinter, PrintResult, PrinterSummary } from '../models/printer-registry.model';

@Injectable({ providedIn: 'root' })
export class PrinterRegistryService {
  private readonly http = inject(HttpClient);

  list(): Observable<PrinterSummary[]> {
    return this.http.get<PrinterSummary[]>('/api/admin/printers');
  }

  discover(): Observable<DiscoveredPrinter[]> {
    return this.http.get<DiscoveredPrinter[]>('/api/admin/printers/discovered');
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
