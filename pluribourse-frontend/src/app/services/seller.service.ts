import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateSellerRequest, PageResponse, SellerDto } from '../models/seller.model';

@Injectable({ providedIn: 'root' })
export class SellerService {
  private readonly http = inject(HttpClient);

  search(query: string): Observable<SellerDto[]> {
    return this.http.get<SellerDto[]>('/api/sellers/search', { params: { query } });
  }

  create(data: CreateSellerRequest): Observable<SellerDto> {
    return this.http.post<SellerDto>('/api/sellers', data);
  }

  getSellers(page: number, size = 50): Observable<PageResponse<SellerDto>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<PageResponse<SellerDto>>('/api/admin/sellers', { params });
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/sellers/${id}`);
  }
}
