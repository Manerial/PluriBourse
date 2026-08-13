import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { PosService } from './pos.service';
import { Basket, Sale, ScanResult, ValidateBasketRequest } from '../models/pos.model';

const MOCK_SCAN_RESULT: ScanResult = {
  itemId: 1,
  name: 'Kapla',
  price: 5,
  incomplete: false,
  comment: null,
  lotId: null,
};

const MOCK_BASKET: Basket = {
  id: 1,
  items: [MOCK_SCAN_RESULT],
  lotGroups: [],
  total: 5,
};

const MOCK_SALE: Sale = {
  id: 1,
  total: 5,
  paymentMethod: 'CASH',
  amountGiven: null,
  changeDue: null,
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

  it('getCurrentBasket() sends GET /api/pos/baskets/current', async () => {
    const p = firstValueFrom(service.getCurrentBasket());
    const req = http.expectOne('/api/pos/baskets/current');
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_BASKET);
    expect(await p).toEqual(MOCK_BASKET);
  });

  it('addItem() sends POST /api/pos/baskets/{basketId}/items with the barcode param', async () => {
    const p = firstValueFrom(service.addItem(1, '00010001'));
    const req = http.expectOne(r => r.url === '/api/pos/baskets/1/items' && r.params.get('barcode') === '00010001');
    expect(req.request.method).toBe('POST');
    req.flush(MOCK_BASKET);
    expect(await p).toEqual(MOCK_BASKET);
  });

  it('removeItem() sends DELETE /api/pos/baskets/{basketId}/items/{itemId}', async () => {
    const p = firstValueFrom(service.removeItem(1, 2));
    const req = http.expectOne('/api/pos/baskets/1/items/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(MOCK_BASKET);
    expect(await p).toEqual(MOCK_BASKET);
  });

  it('removeLot() sends DELETE /api/pos/baskets/{basketId}/lots/{lotId}', async () => {
    const p = firstValueFrom(service.removeLot(1, 2));
    const req = http.expectOne('/api/pos/baskets/1/lots/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(MOCK_BASKET);
    expect(await p).toEqual(MOCK_BASKET);
  });

  it('validate() sends POST /api/pos/baskets/{basketId}/validate with the payload', async () => {
    const dto: ValidateBasketRequest = { paymentMethod: 'CASH', amountGiven: null };
    const p = firstValueFrom(service.validate(1, dto));
    const req = http.expectOne('/api/pos/baskets/1/validate');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(MOCK_SALE);
    expect(await p).toEqual(MOCK_SALE);
  });
});
