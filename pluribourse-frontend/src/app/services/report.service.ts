import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
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

  printEditionReportClosure(editionId: number): Observable<void> {
    return this.http.post<void>(`/api/admin/reports/edition/${editionId}/print-closure`, null);
  }

  exportCatalog(editionId: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`/api/admin/reports/edition/${editionId}/export/catalog`, { responseType: 'blob', observe: 'response' });
  }

  exportSettlements(editionId: number): Observable<HttpResponse<Blob>> {
    return this.http.get(`/api/admin/reports/edition/${editionId}/export/settlements`, { responseType: 'blob', observe: 'response' });
  }
}
