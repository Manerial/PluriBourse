import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { CurrentEditionService } from '../../services/current-edition.service';
import { EditionDto, PhaseType } from '../../models/edition.model';
import { Language } from '../../models/language.enum';
import { settlementPhaseGuard } from './settlement-phase.guard';

describe('settlementPhaseGuard', () => {
  let router: Router;
  let mockEdition: ReturnType<typeof signal<EditionDto | null>>;
  let loadEdition: ReturnType<typeof vi.fn>;

  const edition = (phase: PhaseType): EditionDto => ({
    id: 1,
    name: 'Bourse 2026',
    phase,
    commissionRate: 10,
    documentLanguage: Language.FR,
    createdAt: '2026-01-01',
    archived: false,
    startDate: '2026-01-01',
    endDate: '2026-01-03',
  });

  beforeEach(() => {
    mockEdition = signal<EditionDto | null>(null);
    loadEdition = vi.fn().mockReturnValue(of(undefined));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: CurrentEditionService,
          useValue: { currentEdition: mockEdition, loadEdition },
        },
      ],
    });
    router = TestBed.inject(Router);
  });

  const runGuard = () => TestBed.runInInjectionContext(() => settlementPhaseGuard({} as any, {} as any));

  it('allows activation when the active edition is in the Post-vente phase', async () => {
    mockEdition.set(edition('POST_SALE'));
    expect(await runGuard()).toBe(true);
  });

  it('redirects to /404 when the active edition is in the Sale phase', async () => {
    mockEdition.set(edition('SALE'));
    const result = await runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/404');
  });

  it('redirects to /404 when there is no active edition', async () => {
    mockEdition.set(null);
    const result = await runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/404');
  });

  it('redirects to /404 instead of throwing when loadEdition() rejects', async () => {
    loadEdition.mockReturnValue(EMPTY);
    const result = await runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/404');
  });
});
