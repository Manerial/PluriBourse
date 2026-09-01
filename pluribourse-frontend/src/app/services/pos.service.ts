import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Basket, Sale, SaleListFilter, SaleListPageResponse, ScanResult, ValidateBasketRequest } from '../models/pos.model';

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

  removeLot(basketId: number, lotId: number): Observable<Basket> {
    return this.http.delete<Basket>(`/api/pos/baskets/${basketId}/lots/${lotId}`);
  }

  validate(basketId: number, dto: ValidateBasketRequest): Observable<Sale> {
    return this.http.post<Sale>(`/api/pos/baskets/${basketId}/validate`, dto);
  }

  printInvoice(saleId: number): Observable<void> {
    return this.http.post<void>(`/api/pos/sales/${saleId}/invoice/print`, null);
  }

  listSales(filter: SaleListFilter): Observable<SaleListPageResponse> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    if (filter.dateFrom) {
      params = params.set('dateFrom', filter.dateFrom);
    }
    if (filter.dateTo) {
      params = params.set('dateTo', filter.dateTo);
    }
    if (filter.cashier) {
      params = params.set('cashier', filter.cashier);
    }
    if (filter.sort) {
      params = params.set('sort', filter.sort);
    }
    return this.http.get<SaleListPageResponse>('/api/pos/sales', { params });
  }

  listCashiers(): Observable<string[]> {
    return this.http.get<string[]>('/api/pos/sales/cashiers');
  }
}
