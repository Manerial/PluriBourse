import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CatalogFilter, CreateItemRequest, ItemCatalogPageResponse, ItemCompletenessRequest, ItemDto } from '../models/item.model';

@Injectable({ providedIn: 'root' })
export class ItemService {
  private readonly http = inject(HttpClient);

  create(data: CreateItemRequest): Observable<ItemDto> {
    return this.http.post<ItemDto>('/api/items', data);
  }

  getBySeller(sellerProfileId: number): Observable<ItemDto[]> {
    return this.http.get<ItemDto[]>('/api/items', { params: { sellerProfileId } });
  }

  update(id: number, data: CreateItemRequest): Observable<ItemDto> {
    return this.http.put<ItemDto>(`/api/items/${id}`, data);
  }

  updateCompleteness(id: number, data: ItemCompletenessRequest): Observable<ItemDto> {
    return this.http.patch<ItemDto>(`/api/items/${id}`, data);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/items/${id}`);
  }

  getCatalog(filter: CatalogFilter): Observable<ItemCatalogPageResponse> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    params = this.setIfDefined(params, 'name', filter.name);
    params = this.setIfDefined(params, 'barcode', filter.barcode);
    params = this.setIfDefined(params, 'categoryId', filter.categoryId);
    params = this.setIfDefined(params, 'tableNumber', filter.tableNumber);
    params = this.setIfDefined(params, 'sold', filter.sold);
    params = this.setIfDefined(params, 'incomplete', filter.incomplete);
    params = this.setIfDefined(params, 'sellerName', filter.sellerName);
    params = this.setIfDefined(params, 'sort', filter.sort);
    return this.http.get<ItemCatalogPageResponse>('/api/catalog', { params });
  }

  private setIfDefined(params: HttpParams, key: string, value: string | number | boolean | undefined): HttpParams {
    return value === undefined || value === '' ? params : params.set(key, value);
  }
}
