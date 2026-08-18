import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DailySalesReportDto } from '../models/daily-sales-report.model';
import { EditionSummaryReportDto } from '../models/edition-summary-report.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  getDailyReport(): Observable<DailySalesReportDto> {
    return this.http.get<DailySalesReportDto>('/api/admin/reports/daily');
  }

  printDailyReport(): Observable<void> {
    return this.http.post<void>('/api/admin/reports/daily/print', null);
  }

  getEditionReport(editionId: number): Observable<EditionSummaryReportDto> {
    return this.http.get<EditionSummaryReportDto>(`/api/admin/reports/edition/${editionId}`);
  }

  printEditionReport(editionId: number): Observable<void> {
    return this.http.post<void>(`/api/admin/reports/edition/${editionId}/print`, null);
  }
}
