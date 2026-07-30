import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Basket, Sale, ScanResult, ValidateBasketRequest } from '../models/pos.model';

@Injectable({ providedIn: 'root' })
export class PosService {
  private readonly http = inject(HttpClient);

  scan(barcode: string): Observable<ScanResult> {
    return this.http.get<ScanResult>('/api/pos/scan', { params: { barcode } });
  }

  getCurrentBasket(): Observable<Basket> {
    return this.http.get<Basket>('/api/pos/baskets/current');
  }

  addItem(basketId: number, barcode: string): Observable<Basket> {
    return this.http.post<Basket>(`/api/pos/baskets/${basketId}/items`, null, { params: { barcode } });
  }

  removeItem(basketId: number, itemId: number): Observable<Basket> {
    return this.http.delete<Basket>(`/api/pos/baskets/${basketId}/items/${itemId}`);
  }

  validate(basketId: number, dto: ValidateBasketRequest): Observable<Sale> {
    return this.http.post<Sale>(`/api/pos/baskets/${basketId}/validate`, dto);
  }
}
