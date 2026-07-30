import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PosService } from './pos.service';
import { ScanResult } from '../models/pos.model';

const MOCK_SCAN_RESULT: ScanResult = {
  itemId: 1,
  name: 'Kapla',
  price: 5,
  incomplete: false,
  comment: null,
};

describe('PosService', () => {
  let service: PosService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PosService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('scan() sends GET /api/pos/scan with the barcode param', async () => {
    const p = firstValueFrom(service.scan('00010001'));
    const req = http.expectOne(r => r.url === '/api/pos/scan' && r.params.get('barcode') === '00010001');
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_SCAN_RESULT);
    expect(await p).toEqual(MOCK_SCAN_RESULT);
  });
});
