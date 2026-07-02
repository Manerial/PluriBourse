import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { SellerService } from './seller.service';
import { PageResponse, SellerDto } from '../models/seller.model';

const MOCK_SELLER: SellerDto = { id: 1, firstName: 'Pierre', lastName: 'Martin', email: 'martin.pierre@email.com', phone: '0612345678' };

describe('SellerService', () => {
  let service: SellerService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(SellerService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('search() sends GET /api/sellers/search with query param', async () => {
    const p = firstValueFrom(service.search('martin'));
    const req = http.expectOne(r => r.url === '/api/sellers/search' && r.params.get('query') === 'martin');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_SELLER]);
    expect(await p).toEqual([MOCK_SELLER]);
  });

  it('create() sends POST /api/sellers with seller data', async () => {
    const dto = { firstName: 'Pierre', lastName: 'Martin', email: 'martin.pierre@email.com', phone: '0612345678' };
    const p = firstValueFrom(service.create(dto));
    const req = http.expectOne('/api/sellers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(MOCK_SELLER);
    expect(await p).toEqual(MOCK_SELLER);
  });

  it('getSellers() sends GET /api/admin/sellers with page and size params', async () => {
    const page: PageResponse<SellerDto> = { content: [MOCK_SELLER], totalElements: 1, totalPages: 1, number: 0, size: 50 };
    const p = firstValueFrom(service.getSellers(0, 50));
    const req = http.expectOne(r => r.url === '/api/admin/sellers' && r.params.get('page') === '0' && r.params.get('size') === '50');
    expect(req.request.method).toBe('GET');
    req.flush(page);
    expect(await p).toEqual(page);
  });

  it('delete() sends DELETE /api/admin/sellers/1', async () => {
    const p = firstValueFrom(service.delete(1));
    const req = http.expectOne('/api/admin/sellers/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await p;
  });
});
