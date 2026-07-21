import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DepositService {
  private readonly http = inject(HttpClient);

  validateDeposit(sellerProfileId: number): Observable<void> {
    return this.http.post<void>(`/api/sellers/${sellerProfileId}/deposit/validate`, null);
  }
}
