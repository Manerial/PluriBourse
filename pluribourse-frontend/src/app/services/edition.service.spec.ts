import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import { EditionService } from './edition.service';
import { EditionDto, PhaseType } from '../models/edition.model';
import { Language } from '../models/language.enum';

const MOCK_EDITION: EditionDto = {
  id: 1, name: 'Bourse 2026', phase: 'PREPARATION',
  commissionRate: 20, documentLanguage: Language.EN, createdAt: '2026-01-01',
  archived: false, startDate: '2026-06-01', endDate: '2026-06-03'
};

describe('EditionService', () => {
  let service: EditionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(EditionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('getAll() sends GET /api/admin/editions', async () => {
    const p = firstValueFrom(service.getAll());
    http.expectOne('/api/admin/editions').flush([MOCK_EDITION]);
    expect(await p).toEqual([MOCK_EDITION]);
  });

  it('create() sends POST /api/admin/editions with name, rate, language and dates', async () => {
    const dto = { name: 'Bourse 2026', commissionRate: 20, documentLanguage: 'EN' as const, startDate: '2026-06-01', endDate: '2026-06-03' };
    const p = firstValueFrom(service.create(dto));
    const req = http.expectOne('/api/admin/editions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(dto);
    req.flush(MOCK_EDITION);
    expect(await p).toEqual(MOCK_EDITION);
  });

  it('getById() sends GET /api/admin/editions/1', async () => {
    const p = firstValueFrom(service.getById(1));
    const req = http.expectOne('/api/admin/editions/1');
    expect(req.request.method).toBe('GET');
    req.flush(MOCK_EDITION);
    expect(await p).toEqual(MOCK_EDITION);
  });

  it('update() sends PUT /api/admin/editions/1', async () => {
    const dto = { name: 'Bourse 2026 Modifiée', commissionRate: 15, documentLanguage: 'FR' as const, startDate: '2026-06-01', endDate: '2026-06-03' };
    const p = firstValueFrom(service.update(1, dto));
    const req = http.expectOne('/api/admin/editions/1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(dto);
    req.flush({ ...MOCK_EDITION, ...dto });
    expect((await p).name).toBe('Bourse 2026 Modifiée');
  });

  it('delete() sends DELETE /api/admin/editions/1', async () => {
    const p = firstValueFrom(service.delete(1));
    const req = http.expectOne('/api/admin/editions/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    await p;
  });

  it('advancePhase() sends POST /api/admin/editions/1/phase/advance', async () => {
    const p = firstValueFrom(service.advancePhase(1));
    const req = http.expectOne('/api/admin/editions/1/phase/advance');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_EDITION, phase: 'DEPOSIT' as PhaseType });
    expect((await p).phase).toBe('DEPOSIT');
  });

  it('rollbackPhase() sends POST /api/admin/editions/1/phase/rollback', async () => {
    const p = firstValueFrom(service.rollbackPhase(1));
    const req = http.expectOne('/api/admin/editions/1/phase/rollback');
    expect(req.request.method).toBe('POST');
    req.flush({ ...MOCK_EDITION, phase: 'PREPARATION' as PhaseType });
    expect((await p).phase).toBe('PREPARATION');
  });
});
