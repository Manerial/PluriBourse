import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { SettlementService } from './settlement.service';
import { SettlementDto } from '../models/settlement.model';

const MOCK_SETTLEMENT: SettlementDto = {
  sellerId: 1,
  firstName: 'Alice',
  lastName: 'Vendeuse',
  phone: '0600000001',
  email: 'alice@email.com',
  amountDue: 4.0,
  status: 'UNSETTLED',
};

describe('SettlementService', () => {
  let service: SettlementService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SettlementService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getSettlements() sends GET /api/settlements', async () => {
    const p = firstValueFrom(service.getSettlements());
    const req = http.expectOne('/api/settlements');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_SETTLEMENT]);
    expect(await p).toEqual([MOCK_SETTLEMENT]);
  });

  it('settle() sends POST /api/settlements/1/settle with the given amount', async () => {
    const p = firstValueFrom(service.settle(1, 3.0));
    const req = http.expectOne('/api/settlements/1/settle');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ amount: 3.0 });
    req.flush({ ...MOCK_SETTLEMENT, status: 'SETTLED' });
    expect(await p).toEqual({ ...MOCK_SETTLEMENT, status: 'SETTLED' });
  });

  it('markUnclaimed() sends POST /api/settlements/1/unclaimed', async () => {
    const p = firstValueFrom(service.markUnclaimed(1));
    const req = http.expectOne('/api/settlements/1/unclaimed');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_SETTLEMENT, status: 'UNCLAIMED' });
    expect(await p).toEqual({ ...MOCK_SETTLEMENT, status: 'UNCLAIMED' });
  });

  it('printReport() sends POST /api/settlements/1/report/print', async () => {
    const p = firstValueFrom(service.printReport(1));
    const req = http.expectOne('/api/settlements/1/report/print');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    await p;
  });

  it('printAllReports() sends POST /api/admin/settlements/report/print-all with the uppercased filter', async () => {
    const p = firstValueFrom(service.printAllReports('unsettled'));
    const req = http.expectOne((r) => r.url === '/api/admin/settlements/report/print-all');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeNull();
    expect(req.request.params.get('filter')).toBe('UNSETTLED');
    req.flush({ succeededCount: 1, failedCount: 0 });
    expect(await p).toEqual({ succeededCount: 1, failedCount: 0 });
  });
});
