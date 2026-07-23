import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateLotRequest, LotDto, UpdateLotRequest } from '../models/lot.model';

@Injectable({ providedIn: 'root' })
export class LotService {
  private readonly http = inject(HttpClient);

  create(data: CreateLotRequest): Observable<LotDto> {
    return this.http.post<LotDto>('/api/lots', data);
  }

  update(id: number, data: UpdateLotRequest): Observable<LotDto> {
    return this.http.put<LotDto>(`/api/lots/${id}`, data);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/lots/${id}`);
  }
}
