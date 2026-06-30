import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { CategoryService } from './category.service';
import { EditionCategoryDto } from '../models/category.model';

const MOCK_CATEGORY: EditionCategoryDto = { id: 1, name: 'Jouets', tableNumbers: [1, 2] };

describe('CategoryService', () => {
  let service: CategoryService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CategoryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getCategories() sends GET /api/admin/editions/1/categories', async () => {
    const p = firstValueFrom(service.getCategories(1));
    const req = http.expectOne('/api/admin/editions/1/categories');
    expect(req.request.method).toBe('GET');
    req.flush([MOCK_CATEGORY]);
    expect(await p).toEqual([MOCK_CATEGORY]);
  });

  it('saveCategories() sends PUT /api/admin/editions/1/categories', async () => {
    const payload = [MOCK_CATEGORY];
    const p = firstValueFrom(service.saveCategories(1, payload));
    const req = http.expectOne('/api/admin/editions/1/categories');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush([MOCK_CATEGORY]);
    expect(await p).toEqual([MOCK_CATEGORY]);
  });

  it('copyFromEdition() sends POST /api/admin/editions/1/categories/copy-from/2', async () => {
    const p = firstValueFrom(service.copyFromEdition(1, 2));
    const req = http.expectOne('/api/admin/editions/1/categories/copy-from/2');
    expect(req.request.method).toBe('POST');
    req.flush([MOCK_CATEGORY]);
    expect(await p).toEqual([MOCK_CATEGORY]);
  });
});
