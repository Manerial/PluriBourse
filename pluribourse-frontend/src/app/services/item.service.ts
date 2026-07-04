import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateItemRequest, ItemCompletenessRequest, ItemDto } from '../models/item.model';

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
}
