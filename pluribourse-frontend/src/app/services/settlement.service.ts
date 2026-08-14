import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { SettleRequest, SettlementDto } from '../models/settlement.model';

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
}
