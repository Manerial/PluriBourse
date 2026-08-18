import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DailySalesReportDto } from '../models/daily-sales-report.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly http = inject(HttpClient);

  getDailyReport(): Observable<DailySalesReportDto> {
    return this.http.get<DailySalesReportDto>('/api/admin/reports/daily');
  }

  printDailyReport(): Observable<void> {
    return this.http.post<void>('/api/admin/reports/daily/print', null);
  }
}
