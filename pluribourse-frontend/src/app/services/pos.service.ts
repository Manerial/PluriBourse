import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ScanResult } from '../models/pos.model';

@Injectable({ providedIn: 'root' })
export class PosService {
  private readonly http = inject(HttpClient);

  scan(barcode: string): Observable<ScanResult> {
    return this.http.get<ScanResult>('/api/pos/scan', { params: { barcode } });
  }
}
