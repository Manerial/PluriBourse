import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { EditionCategoryDto } from '../models/category.model';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);

  private base(editionId: number): string {
    return `/api/admin/editions/${editionId}/categories`;
  }

  getCategories(editionId: number): Observable<EditionCategoryDto[]> {
    return this.http.get<EditionCategoryDto[]>(this.base(editionId));
  }

  saveCategories(editionId: number, categories: EditionCategoryDto[]): Observable<EditionCategoryDto[]> {
    return this.http.put<EditionCategoryDto[]>(this.base(editionId), categories);
  }

  copyFromEdition(editionId: number, sourceEditionId: number): Observable<EditionCategoryDto[]> {
    return this.http.post<EditionCategoryDto[]>(
      `${this.base(editionId)}/copy-from/${sourceEditionId}`,
      {}
    );
  }
}
