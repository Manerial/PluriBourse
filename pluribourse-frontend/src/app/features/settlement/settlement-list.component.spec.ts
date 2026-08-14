import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError, Subject } from 'rxjs';
import { vi } from 'vitest';
import { SettlementListComponent } from './settlement-list.component';
import { SettlementService } from '../../services/settlement.service';
import { SettlementDto } from '../../models/settlement.model';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../shared/components/confirm-dialog/confirm-dialog.service';

const ALICE: SettlementDto = {
  sellerId: 1,
  firstName: 'Alice',
  lastName: 'Vendeuse',
  phone: '0600000001',
  email: 'alice@email.com',
  amountDue: 4.0,
  status: 'UNSETTLED',
};

const BOB: SettlementDto = {
  sellerId: 2,
  firstName: 'Bob',
  lastName: 'Vendeur',
  phone: '0600000002',
  email: 'bob@email.com',
  amountDue: 10.0,
  status: 'SETTLED',
};

describe('SettlementListComponent', () => {
  let fixture: ComponentFixture<SettlementListComponent>;
  let component: SettlementListComponent;

  const settlementServiceMock = {
    getSettlements: vi.fn().mockReturnValue(of([ALICE, BOB])),
    settle: vi.fn(),
    markUnclaimed: vi.fn(),
    printReport: vi.fn(),
  };

  const authMock = {
    currentUser: vi.fn().mockReturnValue({ role: 'VOLUNTEER' }),
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  const confirmDialogMock = {
    open: vi.fn().mockReturnValue(of(false)),
  };

  async function setup(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [SettlementListComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: SettlementService, useValue: settlementServiceMock },
        { provide: AuthService, useValue: authMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ConfirmDialogService, useValue: confirmDialogMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SettlementListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.clearAllMocks();
    settlementServiceMock.getSettlements.mockReturnValue(of([ALICE, BOB]));
    authMock.currentUser.mockReturnValue({ role: 'VOLUNTEER' });
    confirmDialogMock.open.mockReturnValue(of(false));
  });

  it('loads settlements on init', async () => {
    await setup();
    expect(settlementServiceMock.getSettlements).toHaveBeenCalledOnce();
    expect(component.settlements().length).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('shows phone/email columns only when the role is ADMIN', async () => {
    authMock.currentUser.mockReturnValue({ role: 'ADMIN' });
    await setup();
    const headerCount = fixture.nativeElement.querySelectorAll('th').length;
    expect(headerCount).toBe(6); // name, phone, email, amountDue, status, actions
  });

  it('hides phone/email columns for a non-admin role', async () => {
    await setup();
    const headerCount = fixture.nativeElement.querySelectorAll('th').length;
    expect(headerCount).toBe(4); // name, amountDue, status, actions
  });

  it('the "unsettled" filter is selected by default and hides settled sellers', async () => {
    await setup();
    expect(component.statusFilter()).toBe('unsettled');
    expect(component.filteredSettlements().map((s) => s.sellerId)).toEqual([1]);
  });

  it('"all" filter shows every seller', async () => {
    await setup();
    component.setStatusFilter('all');
    expect(component.filteredSettlements().map((s) => s.sellerId)).toEqual([1, 2]);
  });

  it('an amount below the due amount shows a warning without blocking', async () => {
    await setup();
    component.openSettleForm(ALICE);
    component.settleAmount.set(3.0);
    expect(component.warningBelowDue()).toBe(true);
    expect(component.blockedAboveDue()).toBe(false);
  });

  it('an amount above the due amount blocks confirmation', async () => {
    await setup();
    component.openSettleForm(ALICE);
    component.settleAmount.set(5.0);
    expect(component.blockedAboveDue()).toBe(true);

    settlementServiceMock.settle.mockReturnValue(of({ ...ALICE, status: 'SETTLED' }));
    await component.confirmSettle(ALICE.sellerId);
    expect(settlementServiceMock.settle).not.toHaveBeenCalled();
  });

  it('a negative amount also blocks confirmation', async () => {
    await setup();
    component.openSettleForm(ALICE);
    component.settleAmount.set(-1.0);
    expect(component.blockedAboveDue()).toBe(true);

    settlementServiceMock.settle.mockReturnValue(of({ ...ALICE, status: 'SETTLED' }));
    await component.confirmSettle(ALICE.sellerId);
    expect(settlementServiceMock.settle).not.toHaveBeenCalled();
  });

  it('a successful settle updates the row and shows a success toast', async () => {
    await setup();
    settlementServiceMock.settle.mockReturnValue(of({ ...ALICE, status: 'SETTLED' }));
    component.openSettleForm(ALICE);
    component.settleAmount.set(3.0);

    await component.confirmSettle(ALICE.sellerId);

    expect(settlementServiceMock.settle).toHaveBeenCalledWith(ALICE.sellerId, 3.0);
    expect(component.settlements().find((s) => s.sellerId === ALICE.sellerId)?.status).toBe('SETTLED');
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.openSettleFormForSellerId()).toBeNull();
  });

  it('a failed settle shows an error toast and keeps the form open', async () => {
    await setup();
    settlementServiceMock.settle.mockReturnValue(throwError(() => new Error('server')));
    component.openSettleForm(ALICE);
    component.settleAmount.set(3.0);

    await component.confirmSettle(ALICE.sellerId);

    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(component.openSettleFormForSellerId()).toBe(ALICE.sellerId);
  });

  it('"unclaimed" opens the confirm dialog then updates the row on confirm', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(true));
    settlementServiceMock.markUnclaimed.mockReturnValue(of({ ...ALICE, status: 'UNCLAIMED' }));

    await component.confirmUnclaimed(ALICE);

    expect(confirmDialogMock.open).toHaveBeenCalledOnce();
    expect(settlementServiceMock.markUnclaimed).toHaveBeenCalledWith(ALICE.sellerId);
    expect(component.settlements().find((s) => s.sellerId === ALICE.sellerId)?.status).toBe('UNCLAIMED');
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('"unclaimed" closes the settle form if it was open for the same seller', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(true));
    settlementServiceMock.markUnclaimed.mockReturnValue(of({ ...ALICE, status: 'UNCLAIMED' }));
    component.openSettleForm(ALICE);

    await component.confirmUnclaimed(ALICE);

    expect(component.openSettleFormForSellerId()).toBeNull();
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('"unclaimed" does nothing when the confirm dialog is cancelled', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(false));

    await component.confirmUnclaimed(ALICE);

    expect(settlementServiceMock.markUnclaimed).not.toHaveBeenCalled();
  });

  it('the deposit slip reprint link is absent when the role is ADMIN', async () => {
    authMock.currentUser.mockReturnValue({ role: 'ADMIN' });
    await setup();
    expect(fixture.nativeElement.querySelector('.reprint-link')).toBeNull();
  });

  it('the deposit slip reprint link is present for a non-admin role', async () => {
    await setup();
    expect(fixture.nativeElement.querySelector('.reprint-link')).not.toBeNull();
  });

  it('the print report button is available on both an unsettled and a settled row', async () => {
    await setup();
    component.setStatusFilter('all');
    fixture.detectChanges();
    const rows: HTMLTableRowElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('tbody tr:not(.settlement-form-row)')
    );
    expect(rows.length).toBe(2);
    // Alice (UNSETTLED): print + settle + unclaimed = 3 buttons. Bob (SETTLED): print only = 1.
    const buttonCounts = rows.map((row) => row.querySelectorAll('.actions-cell button').length);
    expect(buttonCounts.sort()).toEqual([1, 3]);
  });

  it('a successful print report shows a success toast', async () => {
    await setup();
    settlementServiceMock.printReport.mockReturnValue(of(undefined));

    await component.printReport(ALICE);

    expect(settlementServiceMock.printReport).toHaveBeenCalledWith(ALICE.sellerId);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('a 422 invalid-printer-selection error shows the printer-unavailable toast', async () => {
    await setup();
    settlementServiceMock.printReport.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'https://pluribourse/errors/invalid-printer-selection' },
          })
      )
    );

    await component.printReport(ALICE);

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.printerUnavailable');
  });

  it('any other print error shows the generic print-report toast', async () => {
    await setup();
    settlementServiceMock.printReport.mockReturnValue(throwError(() => new Error('server')));

    await component.printReport(ALICE);

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.printReport');
  });

  it('every print button is disabled while one report is in flight, and re-enabled once it settles', async () => {
    await setup();
    const inFlight = new Subject<void>();
    settlementServiceMock.printReport.mockReturnValueOnce(inFlight);

    const printPromise = component.printReport(ALICE);
    expect(component.printingReportForSellerId()).toBe(ALICE.sellerId);

    // A click on a different row's button while the first is still in flight is ignored — the
    // backend print queue is single-threaded per printer anyway (PrintQueueService).
    await component.printReport(BOB);
    expect(settlementServiceMock.printReport).toHaveBeenCalledOnce();
    expect(settlementServiceMock.printReport).not.toHaveBeenCalledWith(BOB.sellerId);

    inFlight.next(undefined);
    inFlight.complete();
    await printPromise;
    expect(component.printingReportForSellerId()).toBeNull();
  });

  it('a second click on the same row while a print is in flight is ignored', async () => {
    await setup();
    const inFlight = new Subject<void>();
    settlementServiceMock.printReport.mockReturnValueOnce(inFlight);

    const first = component.printReport(ALICE);
    await component.printReport(ALICE);
    expect(settlementServiceMock.printReport).toHaveBeenCalledOnce();

    inFlight.next(undefined);
    inFlight.complete();
    await first;
  });
});
