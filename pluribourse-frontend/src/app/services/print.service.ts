import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AvailablePrinter, PrinterSelectionStatus } from '../models/printer.model';

@Injectable({ providedIn: 'root' })
export class PrintService {
  private readonly http = inject(HttpClient);

  getAvailablePrinters(): Observable<AvailablePrinter[]> {
    return this.http.get<AvailablePrinter[]>('/api/printers/available');
  }

  submitSelection(thermalPrinterId: number | null, a4PrinterId: number | null): Observable<PrinterSelectionStatus> {
    return this.http.post<PrinterSelectionStatus>('/api/printers/selection', { thermalPrinterId, a4PrinterId });
  }
}
