import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { CurrentEditionService } from '../../services/current-edition.service';
import { loadEditionOrRedirect } from './edition-load.util';

describe('loadEditionOrRedirect', () => {
  let router: Router;
  let loadEdition: ReturnType<typeof vi.fn>;
  let currentEditionServiceMock: CurrentEditionService;

  beforeEach(() => {
    loadEdition = vi.fn().mockReturnValue(of(undefined));
    currentEditionServiceMock = { loadEdition } as unknown as CurrentEditionService;

    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    router = TestBed.inject(Router);
  });

  it('returns null when loadEdition() resolves', async () => {
    const result = await loadEditionOrRedirect(currentEditionServiceMock, router);
    expect(result).toBeNull();
  });

  it('returns a /404 UrlTree when loadEdition() rejects (EmptyError)', async () => {
    loadEdition.mockReturnValue(EMPTY);
    const result = await loadEditionOrRedirect(currentEditionServiceMock, router);
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/404');
  });
});
