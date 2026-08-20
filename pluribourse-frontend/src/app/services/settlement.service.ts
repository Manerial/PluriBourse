import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BulkSettlementReportPrintResultDto, SettleRequest, SettlementDto, StatusFilter } from '../models/settlement.model';

@Injectable({ providedIn: 'root' })
export class SettlementService {
  private readonly http = inject(HttpClient);

  getSettlements(): Observable<SettlementDto[]> {
    return this.http.get<SettlementDto[]>('/api/settlements');
  }

  settle(sellerId: number, amount: number): Observable<SettlementDto> {
    const body: SettleRequest = { amount };
    return this.http.post<SettlementDto>(`/api/settlements/${sellerId}/settle`, body);
  }

  markUnclaimed(sellerId: number): Observable<SettlementDto> {
    return this.http.post<SettlementDto>(`/api/settlements/${sellerId}/unclaimed`, {});
  }

  printReport(sellerId: number): Observable<void> {
    return this.http.post<void>(`/api/settlements/${sellerId}/report/print`, null);
  }

  printAllReports(filter: StatusFilter): Observable<BulkSettlementReportPrintResultDto> {
    const params = new HttpParams().set('filter', filter.toUpperCase());
    return this.http.post<BulkSettlementReportPrintResultDto>('/api/admin/settlements/report/print-all', null, { params });
  }
}
