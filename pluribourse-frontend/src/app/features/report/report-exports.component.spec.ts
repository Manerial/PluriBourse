import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ReportExportsComponent } from './report-exports.component';
import { ReportService } from '../../services/report.service';
import { ReportEditionScopeService } from './report-edition-scope.service';
import { ToastService } from '../../shared/components/toast/toast.service';

const EDITION_ID = 1;

describe('ReportExportsComponent', () => {
  let fixture: ComponentFixture<ReportExportsComponent>;
  let component: ReportExportsComponent;
  let scope: ReportEditionScopeService;

  const reportServiceMock = {
    exportCatalog: vi.fn().mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) }))),
    exportSettlements: vi.fn().mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) }))),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  async function setup(initialEditionId: number | null): Promise<void> {
    vi.clearAllMocks();
    reportServiceMock.exportCatalog.mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) })));
    reportServiceMock.exportSettlements.mockReturnValue(of(new HttpResponse({ body: new Blob(['csv']) })));
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-url');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await TestBed.configureTestingModule({
      imports: [ReportExportsComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: ReportService, useValue: reportServiceMock },
        ReportEditionScopeService,
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    scope = TestBed.inject(ReportEditionScopeService);
    scope.selectedEditionId.set(initialEditionId);

    fixture = TestBed.createComponent(ReportExportsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  it('renders both export buttons', async () => {
    await setup(EDITION_ID);
    const icons = Array.from(fixture.nativeElement.querySelectorAll('mat-icon')) as Element[];
    const downloadIcons = icons.filter((el) => el.textContent === 'download');
    expect(downloadIcons.length).toBe(2);
  });

  it('exportCatalog() downloads the CSV and shows a success toast', async () => {
    await setup(EDITION_ID);
    await component.exportCatalog();
    expect(reportServiceMock.exportCatalog).toHaveBeenCalledWith(EDITION_ID);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(URL.createObjectURL).toHaveBeenCalledOnce();
    expect(URL.revokeObjectURL).toHaveBeenCalledOnce();
  });

  it('exportCatalog() shows a generic error toast on failure', async () => {
    await setup(EDITION_ID);
    reportServiceMock.exportCatalog.mockReturnValue(throwError(() => new Error('server')));
    await component.exportCatalog();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('exportCatalog() is a no-op while an export is already in flight', async () => {
    await setup(EDITION_ID);
    component.exportingCatalog.set(true);
    await component.exportCatalog();
    expect(reportServiceMock.exportCatalog).not.toHaveBeenCalled();
  });

  it('exportCatalog() is a no-op if no edition is selected', async () => {
    await setup(null);
    await component.exportCatalog();
    expect(reportServiceMock.exportCatalog).not.toHaveBeenCalled();
    expect(toastMock.showError).not.toHaveBeenCalled();
  });

  it('exportSettlements() downloads the CSV and shows a success toast', async () => {
    await setup(EDITION_ID);
    await component.exportSettlements();
    expect(reportServiceMock.exportSettlements).toHaveBeenCalledWith(EDITION_ID);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('exportSettlements() shows a generic error toast on failure', async () => {
    await setup(EDITION_ID);
    reportServiceMock.exportSettlements.mockReturnValue(throwError(() => new Error('server')));
    await component.exportSettlements();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('exportSettlements() is a no-op while an export is already in flight', async () => {
    await setup(EDITION_ID);
    component.exportingSettlements.set(true);
    await component.exportSettlements();
    expect(reportServiceMock.exportSettlements).not.toHaveBeenCalled();
  });
});
