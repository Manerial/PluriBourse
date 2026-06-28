import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EditionDto } from '../models/edition.model';

type EditionRequest = Pick<EditionDto, 'name' | 'commissionRate' | 'documentLanguage'>;

@Injectable({ providedIn: 'root' })
export class EditionService {
  private readonly http = inject(HttpClient);
  private readonly BASE = '/api/admin/editions';

  getAll(): Observable<EditionDto[]> {
    return this.http.get<EditionDto[]>(this.BASE);
  }

  create(dto: EditionRequest): Observable<EditionDto> {
    return this.http.post<EditionDto>(this.BASE, dto);
  }

  getById(id: number): Observable<EditionDto> {
    return this.http.get<EditionDto>(`${this.BASE}/${id}`);
  }

  update(id: number, dto: EditionRequest): Observable<EditionDto> {
    return this.http.put<EditionDto>(`${this.BASE}/${id}`, dto);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.BASE}/${id}`);
  }
}
