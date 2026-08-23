import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EditionReportComponent } from './edition-report.component';
import { ReportService } from '../../services/report.service';
import { ReportEditionScopeService } from './report-edition-scope.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { EditionSummaryReportDto } from '../../models/edition-summary-report.model';

const EDITION_ID = 1;
const OTHER_EDITION_ID = 2;

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

describe('EditionReportComponent', () => {
  let fixture: ComponentFixture<EditionReportComponent>;
  let component: EditionReportComponent;
  let scope: ReportEditionScopeService;

  const reportServiceMock = {
    getEditionReport: vi.fn().mockReturnValue(of(EDITION_REPORT)),
    printEditionReport: vi.fn().mockReturnValue(of(undefined)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  async function setup(
    initialEditionId: number | null,
    editionReportResult: Observable<EditionSummaryReportDto> = of(EDITION_REPORT)
  ): Promise<void> {
    vi.clearAllMocks();
    reportServiceMock.getEditionReport.mockReturnValue(editionReportResult);
    reportServiceMock.printEditionReport.mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [EditionReportComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: ReportService, useValue: reportServiceMock },
        ReportEditionScopeService,
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    scope = TestBed.inject(ReportEditionScopeService);
    scope.selectedEditionId.set(initialEditionId);

    fixture = TestBed.createComponent(EditionReportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('loads the edition report on init', async () => {
    await setup(EDITION_ID);
    expect(reportServiceMock.getEditionReport).toHaveBeenCalledOnce();
    expect(reportServiceMock.getEditionReport).toHaveBeenCalledWith(EDITION_ID);
    expect(component.editionReport()).toEqual(EDITION_REPORT);
  });

  it('does not call the backend when no edition is selected at mount', async () => {
    await setup(null);
    expect(reportServiceMock.getEditionReport).not.toHaveBeenCalled();
    expect(component.editionReport()).toBeNull();
  });

  it('reloads the report when the admin switches the selected edition', async () => {
    await setup(EDITION_ID);
    expect(reportServiceMock.getEditionReport).toHaveBeenCalledWith(EDITION_ID);

    scope.selectedEditionId.set(OTHER_EDITION_ID);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(reportServiceMock.getEditionReport).toHaveBeenCalledWith(OTHER_EDITION_ID);
  });

  it('does not keep refetching once settled (regression: same class of loop bug as the daily report)', async () => {
    await setup(EDITION_ID);
    expect(reportServiceMock.getEditionReport).toHaveBeenCalledOnce();

    await new Promise(resolve => setTimeout(resolve, 20));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(reportServiceMock.getEditionReport).toHaveBeenCalledOnce();
  });

  it('sets a dedicated error key when the edition report fails to load', async () => {
    await setup(EDITION_ID, throwError(() => new Error('network')));
    expect(component.editionReportError()).toBe('admin.reports.error.loadEdition');
  });

  it('printEditionReport() shows a success toast', async () => {
    await setup(EDITION_ID);
    await component.printEditionReport();
    expect(reportServiceMock.printEditionReport).toHaveBeenCalledWith(EDITION_ID);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('a 422 invalid-printer-selection error on print shows the printer-unavailable toast', async () => {
    await setup(EDITION_ID);
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

  it('any other print error shows the generic error toast', async () => {
    await setup(EDITION_ID);
    reportServiceMock.printEditionReport.mockReturnValue(throwError(() => new Error('server')));
    await component.printEditionReport();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('printEditionReport() is a no-op while a print request is already in flight', async () => {
    await setup(EDITION_ID);
    component.printingEditionReport.set(true);
    await component.printEditionReport();
    expect(reportServiceMock.printEditionReport).not.toHaveBeenCalled();
  });

  it('printEditionReport() is a no-op if the selection has turned null since the button was rendered', async () => {
    await setup(EDITION_ID);
    scope.selectedEditionId.set(null);
    await component.printEditionReport();
    expect(reportServiceMock.printEditionReport).not.toHaveBeenCalled();
    expect(toastMock.showError).not.toHaveBeenCalled();
    expect(component.printingEditionReport()).toBe(false);
  });

  it('shows the loading skeleton while the report is being fetched', async () => {
    await setup(EDITION_ID);
    component.isLoadingEditionReport.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('app-skeleton-row').length).toBeGreaterThan(0);
  });

  it('includes the net payout and association revenue totals in the loaded report', async () => {
    await setup(EDITION_ID);
    expect(component.editionReport()?.netPayoutTotal).toBe(EDITION_REPORT.netPayoutTotal);
    expect(component.editionReport()?.associationRevenueTotal).toBe(EDITION_REPORT.associationRevenueTotal);
  });
});
