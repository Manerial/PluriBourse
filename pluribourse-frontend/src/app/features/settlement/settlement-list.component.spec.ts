import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError, Subject } from 'rxjs';
import { vi } from 'vitest';
import { SettlementListComponent } from './settlement-list.component';
import { SettlementService } from '../../services/settlement.service';
import { SseService } from '../../services/sse.service';
import { SettlementDto, SettlementUpdatedEvent } from '../../models/settlement.model';
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
  amountPaid: null,
  status: 'UNSETTLED',
};

const BOB: SettlementDto = {
  sellerId: 2,
  firstName: 'Bob',
  lastName: 'Vendeur',
  phone: '0600000002',
  email: 'bob@email.com',
  amountDue: 10.0,
  amountPaid: 9.5,
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
    printAllReports: vi.fn(),
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

  let settlementUpdated$: Subject<SettlementUpdatedEvent>;
  const sseServiceMock = {
    settlementUpdated: vi.fn(),
  };

  async function setup(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [SettlementListComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: SettlementService, useValue: settlementServiceMock },
        { provide: SseService, useValue: sseServiceMock },
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
    settlementUpdated$ = new Subject<SettlementUpdatedEvent>();
    sseServiceMock.settlementUpdated.mockReturnValue(settlementUpdated$);
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
    expect(headerCount).toBe(7); // name, phone, email, amountDue, amountPaid, status, actions
  });

  it('hides phone/email columns for a non-admin role', async () => {
    await setup();
    const headerCount = fixture.nativeElement.querySelectorAll('th').length;
    expect(headerCount).toBe(5); // name, amountDue, amountPaid, status, actions
  });

  it('the "unsettled" filter is selected by default and hides settled sellers', async () => {
    await setup();
    expect(component.statusFilter()).toBe('unsettled');
    expect(component.filteredSettlements().map((s) => s.sellerId)).toEqual([1]);
  });

  it('"all" filter shows every seller, in deterministic lastName order', async () => {
    await setup();
    component.setStatusFilter('all');
    // BOB ("Vendeur") sorts before ALICE ("Vendeuse") — the list is sorted client-side (story 5.7)
    // since GET /api/settlements guarantees no order.
    expect(component.filteredSettlements().map((s) => s.sellerId)).toEqual([2, 1]);
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
    const settledAlice: SettlementDto = { ...ALICE, status: 'SETTLED', amountPaid: 3.0 };
    settlementServiceMock.settle.mockReturnValue(of(settledAlice));
    // The catch-up loadSettlements(true) in the finally block re-reads the list — the server now
    // returns Alice settled.
    settlementServiceMock.getSettlements.mockReturnValue(of([settledAlice, BOB]));
    component.openSettleForm(ALICE);
    component.settleAmount.set(3.0);

    await component.confirmSettle(ALICE.sellerId);
    await fixture.whenStable();

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
    const unclaimedAlice: SettlementDto = { ...ALICE, status: 'UNCLAIMED' };
    settlementServiceMock.markUnclaimed.mockReturnValue(of(unclaimedAlice));
    settlementServiceMock.getSettlements.mockReturnValue(of([unclaimedAlice, BOB]));

    await component.confirmUnclaimed(ALICE);
    await fixture.whenStable();

    expect(confirmDialogMock.open).toHaveBeenCalledOnce();
    expect(settlementServiceMock.markUnclaimed).toHaveBeenCalledWith(ALICE.sellerId);
    expect(component.settlements().find((s) => s.sellerId === ALICE.sellerId)?.status).toBe('UNCLAIMED');
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('"unclaimed" closes the settle form if it was open for the same seller', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(true));
    const unclaimedAlice: SettlementDto = { ...ALICE, status: 'UNCLAIMED' };
    settlementServiceMock.markUnclaimed.mockReturnValue(of(unclaimedAlice));
    settlementServiceMock.getSettlements.mockReturnValue(of([unclaimedAlice, BOB]));
    component.openSettleForm(ALICE);

    await component.confirmUnclaimed(ALICE);
    await fixture.whenStable();

    expect(component.openSettleFormForSellerId()).toBeNull();
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('"unclaimed" does nothing when the confirm dialog is cancelled', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(false));

    await component.confirmUnclaimed(ALICE);

    expect(settlementServiceMock.markUnclaimed).not.toHaveBeenCalled();
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
    expect(component.anyPrintInFlight()).toBe(true);

    // A click on a different row's button while the first is still in flight is ignored — the
    // backend print queue is single-threaded per printer anyway (PrintQueueService).
    await component.printReport(BOB);
    expect(settlementServiceMock.printReport).toHaveBeenCalledOnce();
    expect(settlementServiceMock.printReport).not.toHaveBeenCalledWith(BOB.sellerId);

    // The grouped button is blocked too, in the same direction.
    await component.printAllReports();
    expect(settlementServiceMock.printAllReports).not.toHaveBeenCalled();

    inFlight.next(undefined);
    inFlight.complete();
    await printPromise;
    expect(component.printingReportForSellerId()).toBeNull();
    expect(component.anyPrintInFlight()).toBe(false);
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

  it('the "Imprimer tous les bilans" button is present for ADMIN, absent for VOLUNTEER', async () => {
    authMock.currentUser.mockReturnValue({ role: 'ADMIN' });
    await setup();
    expect(fixture.nativeElement.querySelector('.print-all-btn')).not.toBeNull();
  });

  it('the "Imprimer tous les bilans" button is absent for a non-admin role', async () => {
    await setup();
    expect(fixture.nativeElement.querySelector('.print-all-btn')).toBeNull();
  });

  it('printAllReports() calls the service with the current status filter', async () => {
    await setup();
    settlementServiceMock.printAllReports.mockReturnValue(of({ succeededCount: 1, failedCount: 0 }));

    await component.printAllReports();
    expect(settlementServiceMock.printAllReports).toHaveBeenCalledWith('unsettled');

    component.setStatusFilter('all');
    await component.printAllReports();
    expect(settlementServiceMock.printAllReports).toHaveBeenCalledWith('all');
  });

  it('failedCount 0 shows a success toast with the succeeded count', async () => {
    await setup();
    settlementServiceMock.printAllReports.mockReturnValue(of({ succeededCount: 2, failedCount: 0 }));
    const instantSpy = vi.spyOn(TestBed.inject(TranslateService), 'instant');

    await component.printAllReports();

    expect(toastMock.showSuccess).toHaveBeenCalledWith('settlement.success.printAll');
    expect(toastMock.showError).not.toHaveBeenCalled();
    // Guards against succeededCount/failedCount being swapped: the translated message itself
    // ('settlement.success.printAll', an untranslated key in this test setup) would look
    // identical either way, so the interpolation params passed to instant() are what actually
    // proves the right count reached the toast.
    expect(instantSpy).toHaveBeenCalledWith('settlement.success.printAll', { count: 2 });
  });

  it('failedCount > 0 shows an error toast with a link to the print queue', async () => {
    await setup();
    settlementServiceMock.printAllReports.mockReturnValue(of({ succeededCount: 1, failedCount: 1 }));
    const instantSpy = vi.spyOn(TestBed.inject(TranslateService), 'instant');

    await component.printAllReports();

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.printAllPartial', {
      path: '/admin/printers/queue',
      label: 'settlement.error.printAllPartialLink',
    });
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
    expect(instantSpy).toHaveBeenCalledWith('settlement.error.printAllPartial', { count: 1 });
  });

  it('a 422 invalid-printer-selection error on the grouped print shows the printer-unavailable toast', async () => {
    await setup();
    settlementServiceMock.printAllReports.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'https://pluribourse/errors/invalid-printer-selection' },
          })
      )
    );

    await component.printAllReports();

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.printerUnavailable');
  });

  it('any other grouped print error shows the generic print-all toast', async () => {
    await setup();
    settlementServiceMock.printAllReports.mockReturnValue(throwError(() => new Error('server')));

    await component.printAllReports();

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.printAll');
  });

  it('the grouped print button is disabled while it is itself in flight, blocking per-row prints too', async () => {
    await setup();
    const inFlight = new Subject<{ succeededCount: number; failedCount: number }>();
    settlementServiceMock.printAllReports.mockReturnValueOnce(inFlight);

    const groupedPromise = component.printAllReports();
    expect(component.printingAll()).toBe(true);
    expect(component.anyPrintInFlight()).toBe(true);

    await component.printReport(ALICE);
    expect(settlementServiceMock.printReport).not.toHaveBeenCalled();

    // A second click on the grouped button itself while it is in flight is also ignored.
    await component.printAllReports();
    expect(settlementServiceMock.printAllReports).toHaveBeenCalledOnce();

    inFlight.next({ succeededCount: 1, failedCount: 0 });
    inFlight.complete();
    await groupedPromise;
    expect(component.printingAll()).toBe(false);
  });

  // ─── Story 5.7 — multi-terminal settlement sync ────────────────────────────────

  function emitRemoteSettlementUpdate(event: SettlementUpdatedEvent = { editionId: 1, sellerId: 1 }): void {
    vi.useFakeTimers();
    try {
      settlementUpdated$.next(event);
      vi.advanceTimersByTime(300); // clears the auditTime(250) window
    } finally {
      vi.useRealTimers();
    }
  }

  it('a remote settlement-updated event triggers a silent reload (no skeleton)', async () => {
    await setup();
    settlementServiceMock.getSettlements.mockClear();

    emitRemoteSettlementUpdate();

    expect(settlementServiceMock.getSettlements).toHaveBeenCalledOnce();
    expect(component.isLoading()).toBe(false);
  });

  it('a remote settlement-updated event is ignored while a local action is in flight', async () => {
    await setup();
    settlementServiceMock.getSettlements.mockClear();
    component.submitting.set(true);

    emitRemoteSettlementUpdate();

    expect(settlementServiceMock.getSettlements).not.toHaveBeenCalled();
  });

  it('a failed silent reload keeps the current list and raises no error banner', async () => {
    await setup();
    expect(component.settlements().length).toBe(2);
    settlementServiceMock.getSettlements.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422 })));

    emitRemoteSettlementUpdate();
    await fixture.whenStable();

    expect(component.settlements().length).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('renders rows in a deterministic order regardless of the server response order', async () => {
    await setup(); // getSettlements → [ALICE, BOB]
    component.setStatusFilter('all');
    const firstOrder = component.filteredSettlements().map((s) => s.sellerId);

    settlementServiceMock.getSettlements.mockReturnValue(of([BOB, ALICE]));
    emitRemoteSettlementUpdate({ editionId: 1, sellerId: 2 });
    await fixture.whenStable();
    const secondOrder = component.filteredSettlements().map((s) => s.sellerId);

    expect(firstOrder).toEqual([2, 1]);
    expect(secondOrder).toEqual([2, 1]);
  });

  it('breaks a lastName tie on firstName, then on sellerId', async () => {
    const zoeMartin: SettlementDto = { ...ALICE, sellerId: 10, firstName: 'Zoe', lastName: 'Martin' };
    const annaMartin: SettlementDto = { ...ALICE, sellerId: 11, firstName: 'Anna', lastName: 'Martin' };
    const annaMartinHomonym: SettlementDto = { ...ALICE, sellerId: 3, firstName: 'Anna', lastName: 'Martin' };
    settlementServiceMock.getSettlements.mockReturnValue(of([zoeMartin, annaMartin, annaMartinHomonym]));
    await setup();
    component.setStatusFilter('all');

    // Anna before Zoe (firstName), and the two Anna Martin homonyms ordered by sellerId (3 before 11).
    expect(component.filteredSettlements().map((s) => s.sellerId)).toEqual([3, 11, 10]);
  });

  it('confirmSettle: a 409 seller-already-settled shows the specific toast and closes the form', async () => {
    await setup();
    settlementServiceMock.settle.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { type: 'https://pluribourse/errors/seller-already-settled' },
          })
      )
    );
    component.openSettleForm(ALICE);
    component.settleAmount.set(3.0);

    await component.confirmSettle(ALICE.sellerId);
    await fixture.whenStable();

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.alreadySettled');
    expect(component.openSettleFormForSellerId()).toBeNull();
  });

  it('confirmSettle: a non-409 error keeps the generic settle-error toast', async () => {
    await setup();
    settlementServiceMock.settle.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 422,
            error: { type: 'https://pluribourse/errors/invalid-settlement-amount' },
          })
      )
    );
    component.openSettleForm(ALICE);
    component.settleAmount.set(3.0);

    await component.confirmSettle(ALICE.sellerId);
    await fixture.whenStable();

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.settle');
  });

  it('confirmSettle triggers a catch-up silent reload in its finally block, even on success', async () => {
    await setup();
    settlementServiceMock.settle.mockReturnValue(of({ ...ALICE, status: 'SETTLED' }));
    settlementServiceMock.getSettlements.mockClear();
    settlementServiceMock.getSettlements.mockReturnValue(of([{ ...ALICE, status: 'SETTLED' }, BOB]));
    component.openSettleForm(ALICE);
    component.settleAmount.set(3.0);

    await component.confirmSettle(ALICE.sellerId);
    await fixture.whenStable();

    expect(settlementServiceMock.getSettlements).toHaveBeenCalledOnce();
  });

  it('confirmUnclaimed: a 409 seller-already-settled shows the specific toast and closes the form if open for that seller', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(true));
    settlementServiceMock.markUnclaimed.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { type: 'https://pluribourse/errors/seller-already-settled' },
          })
      )
    );
    component.openSettleForm(ALICE);

    await component.confirmUnclaimed(ALICE);
    await fixture.whenStable();

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.alreadySettled');
    expect(component.openSettleFormForSellerId()).toBeNull();
  });

  it('confirmUnclaimed: a 409 leaves a settle form open for a different seller untouched', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(true));
    settlementServiceMock.markUnclaimed.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { type: 'https://pluribourse/errors/seller-already-settled' },
          })
      )
    );
    component.openSettleForm(ALICE);

    await component.confirmUnclaimed(BOB);
    await fixture.whenStable();

    expect(toastMock.showError).toHaveBeenCalledWith('settlement.error.alreadySettled');
    expect(component.openSettleFormForSellerId()).toBe(ALICE.sellerId);
  });

  it('confirmUnclaimed triggers a catch-up silent reload in its finally block', async () => {
    await setup();
    confirmDialogMock.open.mockReturnValue(of(true));
    settlementServiceMock.markUnclaimed.mockReturnValue(of({ ...ALICE, status: 'UNCLAIMED' }));
    settlementServiceMock.getSettlements.mockClear();
    settlementServiceMock.getSettlements.mockReturnValue(of([{ ...ALICE, status: 'UNCLAIMED' }, BOB]));

    await component.confirmUnclaimed(ALICE);
    await fixture.whenStable();

    expect(settlementServiceMock.getSettlements).toHaveBeenCalledOnce();
  });

  it('closes the settlement-updated subscription when the component is destroyed', async () => {
    await setup();
    expect(settlementUpdated$.observed).toBe(true);

    fixture.destroy();

    expect(settlementUpdated$.observed).toBe(false);
  });
});
