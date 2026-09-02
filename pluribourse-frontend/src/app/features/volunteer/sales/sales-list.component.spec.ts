import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideTranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { SalesListComponent } from './sales-list.component';
import { PosService } from '../../../services/pos.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SaleListItem, SaleListPageResponse } from '../../../models/pos.model';

const MOCK_SALES: SaleListItem[] = [
  { id: 4, soldAt: '2026-06-12T18:00:00', cashier: 'volunteer2', paymentMethod: 'CASH', total: 12, currency: '€' },
  { id: 1, soldAt: '2026-06-12T09:00:00', cashier: 'volunteer1', paymentMethod: 'CARD', total: 5, currency: '€' },
];

const MOCK_PAGE: SaleListPageResponse = {
  page: { content: MOCK_SALES, totalElements: 2, totalPages: 1, number: 0, size: 50 },
};

describe('SalesListComponent', () => {
  let fixture: ComponentFixture<SalesListComponent>;
  let component: SalesListComponent;

  const posServiceMock = {
    listSales: vi.fn(),
    listCashiers: vi.fn(),
    printInvoice: vi.fn(),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  async function createComponent(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [SalesListComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        provideNativeDateAdapter(),
        { provide: PosService, useValue: posServiceMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SalesListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    // ngOnInit is async (loadCashiers → loadPage): flush its microtask chain past whenStable.
    await new Promise((resolve) => setTimeout(resolve, 0));
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.clearAllMocks();
    posServiceMock.listSales.mockReturnValue(of(MOCK_PAGE));
    posServiceMock.listCashiers.mockReturnValue(of(['volunteer1', 'volunteer2']));
    posServiceMock.printInvoice.mockReturnValue(of(undefined));
  });

  it('loads the cashier selector and the first page on init (AC10, AC12)', async () => {
    await createComponent();
    expect(posServiceMock.listCashiers).toHaveBeenCalledOnce();
    expect(posServiceMock.listSales).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 50 }));
    expect(component.cashiers()).toEqual(['volunteer1', 'volunteer2']);
    expect(component.sales().length).toBe(2);
    expect(component.totalElements()).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('onPageChange loads the requested page (AC10)', async () => {
    await createComponent();
    await component.onPageChange({ pageIndex: 1, pageSize: 50, length: 2 } as PageEvent);
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
    expect(component.pageIndex()).toBe(1);
  });

  it('a date-range filter reloads from page 0 with both bounds serialized to local ISO (AC11)', async () => {
    await createComponent();
    await component.onDateFromChange(new Date(2026, 5, 12, 0, 0, 0));
    await component.onDateToChange(new Date(2026, 5, 12, 23, 59, 0));
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(
      expect.objectContaining({ dateFrom: '2026-06-12T00:00:00', dateTo: '2026-06-12T23:59:00', page: 0 }),
    );
  });

  it('clearing a date bound drops it from the filter', async () => {
    await createComponent();
    await component.onDateFromChange(new Date(2026, 5, 12, 8, 30, 0));
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(
      expect.objectContaining({ dateFrom: '2026-06-12T08:30:00' }),
    );
    await component.onDateFromChange(null);
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(
      expect.objectContaining({ dateFrom: undefined }),
    );
  });

  it('a cashier filter reloads from page 0; "All" clears it (AC12)', async () => {
    await createComponent();
    await component.onCashierChange('volunteer2');
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ cashier: 'volunteer2', page: 0 }));
    await component.onCashierChange(null);
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ cashier: undefined }));
  });

  it('toggles the sort direction on repeated header clicks (AC13)', async () => {
    await createComponent();
    await component.onSortChange({ active: 'total', direction: 'asc' } as Sort);
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'total,asc' }));
    await component.onSortChange({ active: 'total', direction: 'desc' } as Sort);
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'total,desc' }));
    await component.onSortChange({ active: 'total', direction: '' } as Sort);
    expect(posServiceMock.listSales).toHaveBeenLastCalledWith(expect.objectContaining({ sort: undefined }));
  });

  it('discards a stale response that resolves after a newer request', async () => {
    await createComponent();
    const stale = new Subject<SaleListPageResponse>();
    const freshPage: SaleListPageResponse = {
      page: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 },
    };
    posServiceMock.listSales.mockReturnValueOnce(stale.asObservable()).mockReturnValueOnce(of(freshPage));

    const stalePromise = component.onPageChange({ pageIndex: 1, pageSize: 50, length: 2 } as PageEvent);
    await component.onPageChange({ pageIndex: 2, pageSize: 50, length: 2 } as PageEvent);
    stale.next(MOCK_PAGE);
    stale.complete();
    await stalePromise;

    expect(component.totalElements()).toBe(0);
  });

  it('reprints a row and toasts success (AC14)', async () => {
    await createComponent();
    await component.reprint(4);
    expect(posServiceMock.printInvoice).toHaveBeenCalledWith(4);
    expect(toastMock.showSuccess).toHaveBeenCalledWith('volunteer.pos.invoice.success');
    expect(component.reprintInFlightId()).toBeNull();
  });

  it('a 422 on reprint toasts the dedicated A4 message (AC14)', async () => {
    await createComponent();
    posServiceMock.printInvoice.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 422, error: { type: 'https://pluribourse/errors/invalid-printer-selection' } })),
    );
    await component.reprint(4);
    expect(toastMock.showError).toHaveBeenCalledWith('volunteer.pos.invoice.error.a4PrinterUnavailable');
  });

  it('any other reprint error toasts the generic message (AC14)', async () => {
    await createComponent();
    posServiceMock.printInvoice.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: {} })));
    await component.reprint(4);
    expect(toastMock.showError).toHaveBeenCalledWith('volunteer.pos.invoice.error.generic');
  });

  it('a second reprint is ignored while one is already in flight (AC14 double-click lock)', async () => {
    await createComponent();
    const inFlight = new Subject<void>();
    posServiceMock.printInvoice.mockReturnValue(inFlight.asObservable());

    const first = component.reprint(4);
    await component.reprint(1);
    expect(posServiceMock.printInvoice).toHaveBeenCalledTimes(1);

    inFlight.next();
    inFlight.complete();
    await first;
  });

  it('shows the empty state when there are no results (AC15)', async () => {
    posServiceMock.listSales.mockReturnValue(
      of({ page: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 } }),
    );
    await createComponent();
    expect(fixture.nativeElement.querySelector('app-empty-state')).not.toBeNull();
  });

  it('maps a no-active-edition error to its dedicated message (AC15)', async () => {
    posServiceMock.listSales.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { type: 'https://pluribourse/errors/no-active-edition' } })),
    );
    await createComponent();
    expect(component.error()).toBe('volunteer.sales.error.noActiveEdition');
  });

  it('maps any other load error to the generic message (AC15)', async () => {
    posServiceMock.listSales.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: {} })));
    await createComponent();
    expect(component.error()).toBe('volunteer.sales.error.load');
  });

  it('keeps an empty cashier list when the selector call fails', async () => {
    posServiceMock.listCashiers.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: {} })));
    await createComponent();
    expect(component.cashiers()).toEqual([]);
  });
});
