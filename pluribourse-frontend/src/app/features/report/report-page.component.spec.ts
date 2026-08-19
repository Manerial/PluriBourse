import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ReportPageComponent } from './report-page.component';
import { ReportService } from '../../services/report.service';
import { CurrentEditionService } from '../../services/current-edition.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { DailySalesReportDto } from '../../models/daily-sales-report.model';
import { EditionSummaryReportDto } from '../../models/edition-summary-report.model';
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
const CLOSED_EDITION: EditionDto = { ...SALE_EDITION, phase: 'CLOSED' };
const PREPARATION_EDITION: EditionDto = { ...SALE_EDITION, phase: 'PREPARATION' };

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

const EDITION_REPORT: EditionSummaryReportDto = {
  soldItemCount: 3,
  unsoldItemCount: 2,
  grossRevenue: 16.0,
  commission: 1.6,
  cashTotal: 5.0,
  checkTotal: 3.0,
  cardTotal: 8.0,
  netPayoutTotal: 14.4,
  associationRevenueTotal: 1.6,
};

describe('ReportPageComponent', () => {
  let fixture: ComponentFixture<ReportPageComponent>;
  let component: ReportPageComponent;
  let currentEditionService: CurrentEditionService;

  const reportServiceMock = {
    getDailyReport: vi.fn().mockReturnValue(of(DAILY_REPORT)),
    printDailyReport: vi.fn().mockReturnValue(of(undefined)),
    getEditionReport: vi.fn().mockReturnValue(of(EDITION_REPORT)),
    printEditionReport: vi.fn().mockReturnValue(of(undefined)),
    exportCatalog: vi.fn().mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) }))),
    exportSettlements: vi.fn().mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) }))),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  async function setup(initialEdition: EditionDto | null): Promise<void> {
    vi.clearAllMocks();
    reportServiceMock.getDailyReport.mockReturnValue(of(DAILY_REPORT));
    reportServiceMock.printDailyReport.mockReturnValue(of(undefined));
    reportServiceMock.getEditionReport.mockReturnValue(of(EDITION_REPORT));
    reportServiceMock.printEditionReport.mockReturnValue(of(undefined));
    reportServiceMock.exportCatalog.mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) })));
    reportServiceMock.exportSettlements.mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) })));
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

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

  it('shows an empty state outside the Sale and edition-report phases', async () => {
    await setup(PREPARATION_EDITION);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('loads the edition report when the edition is already in Post-vente phase at mount', async () => {
    await setup(POST_SALE_EDITION);
    expect(reportServiceMock.getEditionReport).toHaveBeenCalledOnce();
    expect(reportServiceMock.getEditionReport).toHaveBeenCalledWith(POST_SALE_EDITION.id);
    expect(component.editionReport()).toEqual(EDITION_REPORT);
  });

  it('loads the edition report when the edition is already in Clôturée phase at mount', async () => {
    await setup(CLOSED_EDITION);
    expect(reportServiceMock.getEditionReport).toHaveBeenCalledOnce();
    expect(component.editionReport()).toEqual(EDITION_REPORT);
  });

  it('does not call the backend for the edition report while the edition is in Sale phase', async () => {
    await setup(SALE_EDITION);
    expect(reportServiceMock.getEditionReport).not.toHaveBeenCalled();
    expect(component.editionReport()).toBeNull();
  });

  it('clears the edition report when the phase changes away from Post-vente/Clôturée while the page is open', async () => {
    await setup(POST_SALE_EDITION);
    expect(component.editionReport()).toEqual(EDITION_REPORT);

    currentEditionService.currentEdition.set(SALE_EDITION);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.editionReport()).toBeNull();
  });

  it('sets a dedicated error key when the edition report fails to load', async () => {
    await setup(POST_SALE_EDITION);
    reportServiceMock.getEditionReport.mockReturnValue(throwError(() => new Error('network')));
    currentEditionService.currentEdition.set(SALE_EDITION);
    fixture.detectChanges();
    await fixture.whenStable();
    currentEditionService.currentEdition.set(POST_SALE_EDITION);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.editionReportError()).toBe('admin.reports.error.loadEdition');
  });

  it('printEditionReport() shows a success toast', async () => {
    await setup(POST_SALE_EDITION);
    await component.printEditionReport();
    expect(reportServiceMock.printEditionReport).toHaveBeenCalledWith(POST_SALE_EDITION.id);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('a 422 invalid-printer-selection error on the edition report print shows the printer-unavailable toast', async () => {
    await setup(POST_SALE_EDITION);
    reportServiceMock.printEditionReport.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'https://pluribourse/errors/invalid-printer-selection' },
          })
      )
    );
    await component.printEditionReport();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('any other edition report print error shows the generic error toast', async () => {
    await setup(POST_SALE_EDITION);
    reportServiceMock.printEditionReport.mockReturnValue(throwError(() => new Error('server')));
    await component.printEditionReport();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('printEditionReport() is a no-op while a print request is already in flight', async () => {
    await setup(POST_SALE_EDITION);
    component.printingEditionReport.set(true);
    await component.printEditionReport();
    expect(reportServiceMock.printEditionReport).not.toHaveBeenCalled();
  });

  it('printEditionReport() is a no-op if currentEdition() has turned null since the button was rendered', async () => {
    await setup(POST_SALE_EDITION);
    currentEditionService.currentEdition.set(null);
    await component.printEditionReport();
    expect(reportServiceMock.printEditionReport).not.toHaveBeenCalled();
    expect(toastMock.showError).not.toHaveBeenCalled();
    expect(component.printingEditionReport()).toBe(false);
  });

  it('shows the loading skeleton for the edition report while it is being fetched', async () => {
    await setup(POST_SALE_EDITION);
    component.isLoadingEditionReport.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-skeleton-row').length).toBeGreaterThan(0);
  });

  it('includes the net payout and association revenue totals in the loaded edition report', async () => {
    // report-page.component.html reads editionSummary.netPayoutTotal/associationRevenueTotal
    // directly (plain interpolation, same stat-tile pattern as the 4 pre-existing fields) — the
    // component-level assertion below is this suite's established pattern for verifying
    // async-loaded report data (see "loads the edition report..." tests), since re-running
    // fixture.detectChanges() after the data has resolved re-triggers the component's
    // constructor effect() and its guarded reload, making a further DOM assertion here flaky.
    await setup(POST_SALE_EDITION);
    expect(component.editionReport()?.netPayoutTotal).toBe(EDITION_REPORT.netPayoutTotal);
    expect(component.editionReport()?.associationRevenueTotal).toBe(EDITION_REPORT.associationRevenueTotal);
  });

  it('exportCatalog() downloads the CSV and shows a success toast', async () => {
    await setup(POST_SALE_EDITION);
    await component.exportCatalog();
    expect(reportServiceMock.exportCatalog).toHaveBeenCalledWith(POST_SALE_EDITION.id);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(URL.createObjectURL).toHaveBeenCalledOnce();
    expect(URL.revokeObjectURL).toHaveBeenCalledOnce();
  });

  it('exportCatalog() shows a generic error toast on failure', async () => {
    await setup(POST_SALE_EDITION);
    reportServiceMock.exportCatalog.mockReturnValue(throwError(() => new Error('server')));
    await component.exportCatalog();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('exportCatalog() is a no-op while an export is already in flight', async () => {
    await setup(POST_SALE_EDITION);
    component.exportingCatalog.set(true);
    await component.exportCatalog();
    expect(reportServiceMock.exportCatalog).not.toHaveBeenCalled();
  });

  it('exportCatalog() is a no-op if currentEdition() has turned null since the button was rendered', async () => {
    await setup(POST_SALE_EDITION);
    currentEditionService.currentEdition.set(null);
    await component.exportCatalog();
    expect(reportServiceMock.exportCatalog).not.toHaveBeenCalled();
    expect(toastMock.showError).not.toHaveBeenCalled();
  });

  it('exportSettlements() downloads the CSV and shows a success toast', async () => {
    await setup(POST_SALE_EDITION);
    await component.exportSettlements();
    expect(reportServiceMock.exportSettlements).toHaveBeenCalledWith(POST_SALE_EDITION.id);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('exportSettlements() shows a generic error toast on failure', async () => {
    await setup(POST_SALE_EDITION);
    reportServiceMock.exportSettlements.mockReturnValue(throwError(() => new Error('server')));
    await component.exportSettlements();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('exportSettlements() is a no-op while an export is already in flight', async () => {
    await setup(POST_SALE_EDITION);
    component.exportingSettlements.set(true);
    await component.exportSettlements();
    expect(reportServiceMock.exportSettlements).not.toHaveBeenCalled();
  });

  it('does not render the export section outside Post-vente/Clôturée', async () => {
    await setup(SALE_EDITION);
    const icons = Array.from(fixture.nativeElement.querySelectorAll('mat-icon')) as Element[];
    const downloadIcons = icons.filter((el) => el.textContent === 'download');
    expect(downloadIcons.length).toBe(0);
  });

  it('renders both export buttons in Post-vente/Clôturée', async () => {
    await setup(POST_SALE_EDITION);
    const icons = Array.from(fixture.nativeElement.querySelectorAll('mat-icon')) as Element[];
    const downloadIcons = icons.filter((el) => el.textContent === 'download');
    expect(downloadIcons.length).toBe(2);
  });
});
