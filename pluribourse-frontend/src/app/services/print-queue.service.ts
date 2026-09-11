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

  resumeQueue(printerId: number): Observable<void> {
    return this.http.post<void>(`/api/admin/print-queue/${printerId}/resume`, null);
  }

  discardFailedJob(printerId: number): Observable<void> {
    return this.http.post<void>(`/api/admin/print-queue/${printerId}/discard`, null);
  }
}
