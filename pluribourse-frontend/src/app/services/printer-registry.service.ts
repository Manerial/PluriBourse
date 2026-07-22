import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreatePrinterPayload, PrinterSummary, SerialPortOption } from '../models/printer-registry.model';

@Injectable({ providedIn: 'root' })
export class PrinterRegistryService {
  private readonly http = inject(HttpClient);

  list(): Observable<PrinterSummary[]> {
    return this.http.get<PrinterSummary[]>('/api/admin/printers');
  }

  listSerialPorts(): Observable<SerialPortOption[]> {
    return this.http.get<SerialPortOption[]>('/api/admin/printers/serial-ports');
  }

  create(payload: CreatePrinterPayload): Observable<void> {
    return this.http.post<void>('/api/admin/printers', payload);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/printers/${id}`);
  }
}
