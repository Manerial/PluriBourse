import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ReportPageComponent } from './report-page.component';
import { ReportService } from '../../services/report.service';
import { CurrentEditionService } from '../../services/current-edition.service';
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

  const reportServiceMock = {
    getDailyReport: vi.fn().mockReturnValue(of(DAILY_REPORT)),
    printDailyReport: vi.fn().mockReturnValue(of(undefined)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  async function setup(initialEdition: EditionDto | null): Promise<void> {
    vi.clearAllMocks();
    reportServiceMock.getDailyReport.mockReturnValue(of(DAILY_REPORT));
    reportServiceMock.printDailyReport.mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [ReportPageComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: ReportService, useValue: reportServiceMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    currentEditionService = TestBed.inject(CurrentEditionService);
    currentEditionService.currentEdition.set(initialEdition);

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
    await setup({ ...SALE_EDITION, phase: 'POST_SALE' });
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

    currentEditionService.currentEdition.set({ ...SALE_EDITION, phase: 'POST_SALE' });
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.report()).toBeNull();
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

  it('shows an empty state outside the Sale phase', async () => {
    await setup({ ...SALE_EDITION, phase: 'POST_SALE' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });
});
