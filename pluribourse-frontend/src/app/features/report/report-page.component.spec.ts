import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatSelectChange } from '@angular/material/select';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ReportPageComponent } from './report-page.component';
import { ReportService } from '../../services/report.service';
import { EditionService } from '../../services/edition.service';
import { CurrentEditionService } from '../../services/current-edition.service';
import { ReportEditionScopeService } from './report-edition-scope.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DailySalesReportDto } from '../../models/daily-sales-report.model';
import { EditionDto } from '../../models/edition.model';
import { Language } from '../../models/language.enum';

const SALE_EDITION: EditionDto = {
  id: 1,
  name: 'Bourse Test',
  phase: 'SALE',
  commissionRate: 10,
  documentLanguage: Language.FR,
  createdAt: '2026-01-01',
  archived: false,
  startDate: '2026-01-01',
  endDate: '2026-01-03',
};

const POST_SALE_EDITION: EditionDto = { ...SALE_EDITION, phase: 'POST_SALE' };
const PREPARATION_EDITION: EditionDto = { ...SALE_EDITION, phase: 'PREPARATION' };
const OLDER_CLOSED_EDITION: EditionDto = {
  ...SALE_EDITION,
  id: 2,
  name: 'Bourse Précédente',
  phase: 'CLOSED',
  startDate: '2025-01-01',
  endDate: '2025-01-03',
};

const DAILY_REPORT: DailySalesReportDto = {
  reportDate: '2026-08-18',
  soldItemCount: 2,
  unsoldItemCount: 1,
  grossRevenue: 13.0,
  commission: 1.3,
  cashTotal: 5.0,
  checkTotal: 0.0,
  cardTotal: 8.0,
};

describe('ReportPageComponent', () => {
  let fixture: ComponentFixture<ReportPageComponent>;
  let component: ReportPageComponent;
  let currentEditionService: CurrentEditionService;
  let scope: ReportEditionScopeService;

  const reportServiceMock = {
    getDailyReport: vi.fn().mockReturnValue(of(DAILY_REPORT)),
    printDailyReport: vi.fn().mockReturnValue(of(undefined)),
  };
  const editionServiceMock = {
    getAll: vi.fn().mockReturnValue(of([])),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  async function setup(initialEdition: EditionDto | null, editions: EditionDto[] = []): Promise<void> {
    vi.clearAllMocks();
    reportServiceMock.getDailyReport.mockReturnValue(of(DAILY_REPORT));
    reportServiceMock.printDailyReport.mockReturnValue(of(undefined));
    editionServiceMock.getAll.mockReturnValue(of(editions));

    await TestBed.configureTestingModule({
      imports: [ReportPageComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: ReportService, useValue: reportServiceMock },
        { provide: EditionService, useValue: editionServiceMock },
        ReportEditionScopeService,
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    TestBed.inject(TranslateService).setTranslation('en', {
      admin: { reports: { tabs: { edition: 'Edition summary', exports: 'Exports' } } },
    });

    currentEditionService = TestBed.inject(CurrentEditionService);
    currentEditionService.currentEdition.set(initialEdition);
    scope = TestBed.inject(ReportEditionScopeService);

    fixture = TestBed.createComponent(ReportPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('loads the daily report when the edition is already in Sale phase at mount', async () => {
    await setup(SALE_EDITION);
    expect(reportServiceMock.getDailyReport).toHaveBeenCalledOnce();
    expect(component.report()).toEqual(DAILY_REPORT);
  });

  it('does not call the backend when the edition is not in Sale phase at mount', async () => {
    await setup(PREPARATION_EDITION);
    expect(reportServiceMock.getDailyReport).not.toHaveBeenCalled();
    expect(component.report()).toBeNull();
  });

  it('loads the daily report reactively once currentEdition() resolves to Sale phase after mount', async () => {
    // Simulates AppLayoutComponent.loadEdition() resolving after this component's construction —
    // the whole reason report-page uses an effect() instead of a one-shot ngOnInit check.
    await setup(null);
    expect(reportServiceMock.getDailyReport).not.toHaveBeenCalled();

    currentEditionService.currentEdition.set(SALE_EDITION);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(reportServiceMock.getDailyReport).toHaveBeenCalledOnce();
    expect(component.report()).toEqual(DAILY_REPORT);
  });

  it('clears the report when the phase changes away from Sale while the page is open', async () => {
    await setup(SALE_EDITION);
    expect(component.report()).toEqual(DAILY_REPORT);

    currentEditionService.currentEdition.set(POST_SALE_EDITION);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.report()).toBeNull();
  });

  it('does not keep refetching the daily report once settled (regression: effect must not depend on isLoading)', async () => {
    // load() reads isLoading() synchronously before its first await, then its finally block flips
    // that same signal back after settling — if that read were tracked as a dependency of the
    // constructor effect(), the resulting write would re-trigger the effect and call the backend
    // again, forever, paced only by how fast the mock/network resolves.
    await setup(SALE_EDITION);
    expect(reportServiceMock.getDailyReport).toHaveBeenCalledOnce();

    await new Promise(resolve => setTimeout(resolve, 20));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(reportServiceMock.getDailyReport).toHaveBeenCalledOnce();
  });

  it('refresh() re-fetches the daily report', async () => {
    await setup(SALE_EDITION);
    reportServiceMock.getDailyReport.mockClear();
    await component.refresh();
    expect(reportServiceMock.getDailyReport).toHaveBeenCalledOnce();
  });

  it('sets a dedicated error key when the daily report fails to load', async () => {
    await setup(SALE_EDITION);
    reportServiceMock.getDailyReport.mockReturnValue(throwError(() => new Error('network')));
    await component.refresh();
    expect(component.error()).toBe('admin.reports.error.load');
  });

  it('printReport() shows a success toast', async () => {
    await setup(SALE_EDITION);
    await component.printReport();
    expect(reportServiceMock.printDailyReport).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('a 422 invalid-printer-selection error shows the printer-unavailable toast', async () => {
    await setup(SALE_EDITION);
    reportServiceMock.printDailyReport.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'https://pluribourse/errors/invalid-printer-selection' },
          })
      )
    );
    await component.printReport();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('any other print error shows the generic error toast', async () => {
    await setup(SALE_EDITION);
    reportServiceMock.printDailyReport.mockReturnValue(throwError(() => new Error('server')));
    await component.printReport();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('printReport() is a no-op while a print request is already in flight', async () => {
    await setup(SALE_EDITION);
    component.printing.set(true);
    await component.printReport();
    expect(reportServiceMock.printDailyReport).not.toHaveBeenCalled();
  });

  it('shows the loading skeleton while the report is being fetched', async () => {
    await setup(SALE_EDITION);
    component.isLoading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-skeleton-row')).not.toBeNull();
  });

  it('shows an empty state when no edition has ever reached Post-vente', async () => {
    await setup(PREPARATION_EDITION, []);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('does not render the edition/exports tab bar when no edition is reportable', async () => {
    await setup(SALE_EDITION, []);
    expect(fixture.nativeElement.querySelector('[mat-tab-nav-bar]')).toBeNull();
  });

  it('renders the edition/exports tab bar as soon as an edition is reportable, current or past', async () => {
    await setup(POST_SALE_EDITION, [POST_SALE_EDITION]);
    // The default selection is applied inside the async loadEditions() chain (an effect(), not a
    // one-shot ngOnInit) — whenStable() settles the underlying signal write, but rendering the
    // @if block gated on it needs one more explicit detectChanges() pass, same as any other
    // post-whenStable() signal write asserted against the DOM rather than the component itself.
    fixture.detectChanges();
    const links: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('a[mat-tab-link]'));
    const labels = links.map(el => el.textContent?.trim());
    expect(labels).toContain('Edition summary');
    expect(labels).toContain('Exports');
  });

  it('only lists Post-vente/Clôturée editions as reportable, excluding earlier phases', async () => {
    await setup(SALE_EDITION, [SALE_EDITION, POST_SALE_EDITION, OLDER_CLOSED_EDITION, PREPARATION_EDITION]);
    expect(component.reportableEditions().map(e => e.id)).toEqual([POST_SALE_EDITION.id, OLDER_CLOSED_EDITION.id]);
  });

  it('defaults the selection to the current edition when it is itself reportable', async () => {
    await setup(POST_SALE_EDITION, [OLDER_CLOSED_EDITION, POST_SALE_EDITION]);
    expect(scope.selectedEditionId()).toBe(POST_SALE_EDITION.id);
  });

  it('defaults the selection to the most recent reportable edition when the current one is not reportable', async () => {
    await setup(SALE_EDITION, [OLDER_CLOSED_EDITION]);
    expect(scope.selectedEditionId()).toBe(OLDER_CLOSED_EDITION.id);
  });

  it('does not override a selection the admin already made', async () => {
    await setup(POST_SALE_EDITION, [OLDER_CLOSED_EDITION, POST_SALE_EDITION]);
    component.onEditionChange({ value: OLDER_CLOSED_EDITION.id } as MatSelectChange);
    expect(scope.selectedEditionId()).toBe(OLDER_CLOSED_EDITION.id);

    // A new object (not the same reference as the initial POST_SALE_EDITION), so the currentEdition
    // signal genuinely changes and the default-selection effect re-runs — it must still see a
    // selection already made and leave it alone.
    currentEditionService.currentEdition.set({ ...POST_SALE_EDITION });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(scope.selectedEditionId()).toBe(OLDER_CLOSED_EDITION.id);
  });

  it('onEditionChange() updates the shared selection', async () => {
    await setup(POST_SALE_EDITION, [POST_SALE_EDITION, OLDER_CLOSED_EDITION]);
    component.onEditionChange({ value: OLDER_CLOSED_EDITION.id } as MatSelectChange);
    expect(scope.selectedEditionId()).toBe(OLDER_CLOSED_EDITION.id);
  });
});
