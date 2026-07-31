import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class DepositService {
  private readonly http = inject(HttpClient);

  reprintLabels(sellerProfileId: number): Observable<void> {
    return this.http.post<void>(`/api/sellers/${sellerProfileId}/deposit/labels/reprint`, null);
  }

  reprintDepositSlip(sellerProfileId: number): Observable<void> {
    return this.http.post<void>(`/api/sellers/${sellerProfileId}/deposit/slip/reprint`, null);
  }
}
