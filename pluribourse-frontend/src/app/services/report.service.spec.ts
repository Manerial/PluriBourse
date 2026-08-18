import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { ReportService } from './report.service';
import { DailySalesReportDto } from '../models/daily-sales-report.model';

const MOCK_REPORT: DailySalesReportDto = {
  reportDate: '2026-08-18',
  soldItemCount: 2,
  unsoldItemCount: 1,
  grossRevenue: 13.0,
  commission: 1.3,
  cashTotal: 5.0,
  checkTotal: 0.0,
  cardTotal: 8.0,
};

describe('ReportService', () => {
  let service: ReportService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ReportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getDailyReport() sends GET /api/admin/reports/daily', async () => {
    const p = firstValueFrom(service.getDailyReport());
    const req = http.expectOne('/api/admin/reports/daily');
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_REPORT);
    expect(await p).toEqual(MOCK_REPORT);
  });

  it('printDailyReport() sends POST /api/admin/reports/daily/print', async () => {
    const p = firstValueFrom(service.printDailyReport());
    const req = http.expectOne('/api/admin/reports/daily/print');
    expect(req.request.method).toBe('POST');
    req.flush(null);
    await p;
  });
});
