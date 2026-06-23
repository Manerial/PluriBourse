import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { GlobalInstanceConfigDto } from '../models/global-instance-config.model';

@Injectable({ providedIn: 'root' })
export class GlobalInstanceConfigService {
  private readonly http = inject(HttpClient);

  getConfig(): Observable<GlobalInstanceConfigDto> {
    return this.http.get<GlobalInstanceConfigDto>('/api/admin/instance-config');
  }

  updateConfig(data: GlobalInstanceConfigDto): Observable<GlobalInstanceConfigDto> {
    return this.http.put<GlobalInstanceConfigDto>('/api/admin/instance-config', data);
  }
}
