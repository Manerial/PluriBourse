import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PrinterStatus } from '../models/printer-status.model';

@Injectable({ providedIn: 'root' })
export class PrintQueueService {
  private readonly http = inject(HttpClient);

  getStatuses(): Observable<PrinterStatus[]> {
    return this.http.get<PrinterStatus[]>('/api/admin/print-queue');
  }

  // Live-checks every printer's connectivity through PrinterBridge before returning fresh
  // statuses — unlike getStatuses(), which only reads the cached in-memory state.
  refreshStatuses(): Observable<PrinterStatus[]> {
    return this.http.post<PrinterStatus[]>('/api/admin/print-queue/refresh', null);
  }

  resumeQueue(printerId: number): Observable<void> {
    return this.http.post<void>(`/api/admin/print-queue/${printerId}/resume`, null);
  }

  discardFailedJob(printerId: number): Observable<void> {
    return this.http.post<void>(`/api/admin/print-queue/${printerId}/discard`, null);
  }
}
