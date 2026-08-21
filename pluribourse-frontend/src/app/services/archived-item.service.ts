import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ArchivedCatalogFilter, ArchivedItemPageResponse } from '../models/archived-item.model';

@Injectable({ providedIn: 'root' })
export class ArchivedItemService {
  private readonly http = inject(HttpClient);

  getArchivedCatalog(editionId: number, filter: ArchivedCatalogFilter): Observable<ArchivedItemPageResponse> {
    let params = new HttpParams().set('page', filter.page).set('size', filter.size);
    params = this.setIfDefined(params, 'name', filter.name);
    params = this.setIfDefined(params, 'categoryName', filter.categoryName);
    params = this.setIfDefined(params, 'sold', filter.sold);
    params = this.setIfDefined(params, 'sort', filter.sort);
    return this.http.get<ArchivedItemPageResponse>(`/api/admin/archive/editions/${editionId}/items`, { params });
  }

  private setIfDefined(params: HttpParams, key: string, value: string | number | boolean | undefined): HttpParams {
    return value === undefined || value === '' ? params : params.set(key, value);
  }
}
