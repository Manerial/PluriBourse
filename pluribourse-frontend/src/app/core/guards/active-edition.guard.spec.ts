import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { EMPTY, of } from 'rxjs';
import { CurrentEditionService } from '../../services/current-edition.service';
import { activeEditionGuard } from './active-edition.guard';

describe('activeEditionGuard', () => {
  let router: Router;
  let loadEdition: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    loadEdition = vi.fn().mockReturnValue(of(undefined));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: CurrentEditionService,
          useValue: { loadEdition },
        },
      ],
    });
    router = TestBed.inject(Router);
  });

  const runGuard = () => TestBed.runInInjectionContext(() => activeEditionGuard({} as any, {} as any));

  it('allows activation when loadEdition() succeeds (an edition is active)', async () => {
    expect(await runGuard()).toBe(true);
  });

  it('redirects to /404 when there is no active edition (loadEdition() fails)', async () => {
    loadEdition.mockReturnValue(EMPTY);
    const result = await runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/404');
  });
});
