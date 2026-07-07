import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { ItemService } from './item.service';
import { CreateItemRequest, ItemDto } from '../models/item.model';

const MOCK_ITEM: ItemDto = {
  id: 1,
  sellerProfileId: 10,
  categoryId: 100,
  categoryName: 'Jouets',
  name: 'Kapla',
  price: 5,
  incomplete: false,
  comment: null,
  tableNumber: 1,
  lotId: null,
  lotName: null,
  lotPrice: null,
};

describe('ItemService', () => {
  let service: ItemService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(ItemService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('create() sends POST /api/items with item data', async () => {
    const dto: CreateItemRequest = { sellerProfileId: 10, categoryId: 100, name: 'Kapla', price: 5, incomplete: false, comment: null };
    const p = firstValueFrom(service.create(dto));
    const req = http.expectOne('/api/items');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(MOCK_ITEM);
    expect(await p).toEqual(MOCK_ITEM);
  });

  it('getBySeller() sends GET /api/items with sellerProfileId param', async () => {
    const p = firstValueFrom(service.getBySeller(10));
    const req = http.expectOne(r => r.url === '/api/items' && r.params.get('sellerProfileId') === '10');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_ITEM]);
    expect(await p).toEqual([MOCK_ITEM]);
  });

  it('update() sends PUT /api/items/1 with item data', async () => {
    const dto: CreateItemRequest = { sellerProfileId: 10, categoryId: 100, name: 'Kapla neuf', price: 6, incomplete: false, comment: null };
    const p = firstValueFrom(service.update(1, dto));
    const req = http.expectOne('/api/items/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush(MOCK_ITEM);
    expect(await p).toEqual(MOCK_ITEM);
  });

  it('updateCompleteness() sends PATCH /api/items/1 with completeness data', async () => {
    const dto = { incomplete: true, comment: 'Piece manquante' };
    const p = firstValueFrom(service.updateCompleteness(1, dto));
    const req = http.expectOne('/api/items/1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(dto);
    req.flush(MOCK_ITEM);
    expect(await p).toEqual(MOCK_ITEM);
  });

  it('delete() sends DELETE /api/items/1', async () => {
    const p = firstValueFrom(service.delete(1));
    const req = http.expectOne('/api/items/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await p;
  });
});
