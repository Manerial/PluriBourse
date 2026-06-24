import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Language } from '../models/language.enum';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  updateLanguage(language: Language): Observable<void> {
    return this.http.put<void>('/api/account/language-preference', null, { params: { language } });
  }
}
