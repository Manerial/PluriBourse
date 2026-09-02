import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import Big from 'big.js';
import { auditTime, firstValueFrom } from 'rxjs';
import { StatusFilter, SettlementDto } from '../../models/settlement.model';
import { SettlementService } from '../../services/settlement.service';
import { SseService } from '../../services/sse.service';
import { AuthService } from '../../services/auth.service';
import { CurrentEditionService } from '../../services/current-edition.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../shared/components/confirm-dialog/confirm-dialog.service';
import { SkeletonRowComponent } from '../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../shared/components/notification-inline/notification-inline.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { extractErrorType } from '../../shared/http-error.util';

@Component({
  selector: 'app-settlement-list',
  standalone: true,
  imports: [
    MatButtonModule,
    MatCheckboxModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslatePipe,
    SkeletonRowComponent,
    NotificationInlineComponent,
    EmptyStateComponent,
  ],
  templateUrl: './settlement-list.component.html',
  styleUrl: './settlement-list.component.scss',
})
export class SettlementListComponent implements OnInit {
  private readonly settlementService = inject(SettlementService);
  private readonly auth = inject(AuthService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly sseService = inject(SseService);
  private readonly destroyRef = inject(DestroyRef);

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');
  readonly currency = computed(() => this.currentEditionService.currentEdition()?.currency);

  readonly settlements = signal<SettlementDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly statusFilter = signal<StatusFilter>('unsettled');
  readonly filteredSettlements = computed(() => {
    const filter = this.statusFilter();
    return this.settlements()
      .filter((s) =>
        filter === 'all' ? true : filter === 'unsettled' ? s.status === 'UNSETTLED' : s.status !== 'UNSETTLED'
      )
      // GET /api/settlements guarantees no order; with a silent reload on every remote
      // settlement-updated event the row order would otherwise drift between reloads and
      // flicker on every terminal. Sort client-side (lastName then firstName — SettlementDto
      // carries no sellerNumber); no backend ORDER BY, no AC requires one (story 5.7). sellerId
      // is the final tie-break so homonyms (same lastName + firstName) still get a stable order.
      .sort(
        (a, b) =>
          a.lastName.localeCompare(b.lastName) ||
          a.firstName.localeCompare(b.firstName) ||
          a.sellerId - b.sellerId
      );
  });

  readonly openSettleFormForSellerId = signal<number | null>(null);
  readonly settleAmount = signal<number | null>(null);
  readonly printingReportForSellerId = signal<number | null>(null);
  readonly printingAll = signal(false);

  // Story 5.8: the settle form offers to print the sales report right after a successful settle;
  // checked by default, reset to checked every time the form opens (openSettleForm) or closes
  // (closeSettleForm).
  readonly printReportOnSettle = signal(true);

  // Both the per-row print buttons and the grouped button share this guard: the backend print
  // queue is single-threaded per printer (PrintQueueService), so an individual send in flight
  // blocks the grouped button and vice versa — same rationale already documented on
  // printingReportForSellerId, applied consistently rather than as two independent guards.
  readonly anyPrintInFlight = computed(() => this.printingReportForSellerId() !== null || this.printingAll());

  private readonly openSettlement = computed(() => {
    const id = this.openSettleFormForSellerId();
    return id === null ? null : (this.settlements().find((s) => s.sellerId === id) ?? null);
  });

  readonly warningBelowDue = computed(() => {
    const settlement = this.openSettlement();
    const amount = this.settleAmount();
    if (!settlement || amount === null) {
      return false;
    }
    return new Big(amount).lt(settlement.amountDue);
  });

  readonly blockedAboveDue = computed(() => {
    const settlement = this.openSettlement();
    const amount = this.settleAmount();
    if (!settlement || amount === null) {
      return false;
    }
    const big = new Big(amount);
    return big.gt(settlement.amountDue) || big.lt(0);
  });

  readonly warningMessage = computed(() => {
    const settlement = this.openSettlement();
    const amount = this.settleAmount();
    if (!settlement || amount === null) {
      return '';
    }
    return this.translate.instant('settlement.form.warningBelowDue', {
      amount: amount.toFixed(2),
      due: settlement.amountDue.toFixed(2),
      currency: this.currency(),
    });
  });

  constructor() {
    // Another terminal settled (or marked Non réclamé) a seller — refresh this list so the row
    // takes its new status and leaves the active filter. auditTime absorbs a burst (concurrent
    // settlements across a handful of terminals); the emitting terminal's own echo is ignored in
    // onRemoteSettlementUpdate while its optimistic applyUpdate is authoritative for it.
    this.sseService
      .settlementUpdated()
      .pipe(auditTime(250), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.onRemoteSettlementUpdate());
  }

  async ngOnInit(): Promise<void> {
    await this.loadSettlements();
  }

  private onRemoteSettlementUpdate(): void {
    if (this.submitting()) {
      // A local settle/markUnclaimed is in flight: its applyUpdate is the source of truth for
      // this terminal, and the loadSettlements(true) in its finally block picks up whatever
      // remote change this event carried.
      return;
    }
    void this.loadSettlements(true);
  }

  setStatusFilter(filter: StatusFilter): void {
    this.statusFilter.set(filter);
  }

  openSettleForm(settlement: SettlementDto): void {
    this.openSettleFormForSellerId.set(settlement.sellerId);
    this.settleAmount.set(settlement.amountDue);
    // AC-B1: the box is checked on every open — including a direct switch from another seller's
    // form, which closes via the @if without running closeSettleForm().
    this.printReportOnSettle.set(true);
  }

  closeSettleForm(): void {
    this.openSettleFormForSellerId.set(null);
    this.settleAmount.set(null);
    this.printReportOnSettle.set(true);
  }

  onSettleAmountChange(event: Event): void {
    const value = (event.target as HTMLInputElement).valueAsNumber;
    this.settleAmount.set(Number.isNaN(value) ? null : value);
  }

  async confirmSettle(sellerId: number): Promise<void> {
    const amount = this.settleAmount();
    if (this.blockedAboveDue() || amount === null) {
      return;
    }
    this.submitting.set(true);
    try {
      const updated = await firstValueFrom(this.settlementService.settle(sellerId, amount));
      this.applyUpdate(updated);
      this.toast.showSuccess(this.translate.instant('settlement.success.settle'));
      // Capture the checkbox before closeSettleForm() resets it to checked.
      const shouldPrint = this.printReportOnSettle();
      this.closeSettleForm();
      if (shouldPrint) {
        void this.autoPrintReport(sellerId);
      }
    } catch (err: unknown) {
      if (this.isAlreadySettledConflict(err)) {
        // Another terminal won the race: NFR-008 wants a specific message, not the generic one.
        this.toast.showError(this.translate.instant('settlement.error.alreadySettled'));
        this.closeSettleForm();
      } else {
        // Server is the actual source of truth (never trust the client alone, cf. PosBasketService)
        // — a 422 here is unlikely since the amount is already blocked client-side, but still handled.
        this.toast.showError(this.translate.instant('settlement.error.settle'));
      }
    } finally {
      this.submitting.set(false);
      // Catch-up reload: realigns the row on the real server state after a 409, and picks up any
      // remote settlement-updated events ignored by onRemoteSettlementUpdate while submitting was
      // true. Idempotent (track sellerId + deterministic sort), silent, no error banner.
      void this.loadSettlements(true);
    }
  }

  async confirmUnclaimed(settlement: SettlementDto): Promise<void> {
    const confirmed = await firstValueFrom(
      this.confirmDialog.open({
        title: this.translate.instant('settlement.unclaimedDialog.title'),
        description: this.translate.instant('settlement.unclaimedDialog.description', {
          amount: settlement.amountDue.toFixed(2),
          currency: this.currency(),
        }),
        confirmVariant: 'error',
      })
    );
    if (!confirmed) {
      return;
    }
    this.submitting.set(true);
    try {
      const updated = await firstValueFrom(this.settlementService.markUnclaimed(settlement.sellerId));
      this.applyUpdate(updated);
      this.toast.showSuccess(this.translate.instant('settlement.success.unclaimed'));
      if (this.openSettleFormForSellerId() === settlement.sellerId) {
        this.closeSettleForm();
      }
    } catch (err: unknown) {
      if (this.isAlreadySettledConflict(err)) {
        this.toast.showError(this.translate.instant('settlement.error.alreadySettled'));
        if (this.openSettleFormForSellerId() === settlement.sellerId) {
          this.closeSettleForm();
        }
      } else {
        this.toast.showError(this.translate.instant('settlement.error.settle'));
      }
    } finally {
      this.submitting.set(false);
      void this.loadSettlements(true);
    }
  }

  private isAlreadySettledConflict(err: unknown): boolean {
    return (
      err instanceof HttpErrorResponse &&
      err.status === 409 &&
      (extractErrorType(err)?.endsWith('/seller-already-settled') ?? false)
    );
  }

  async printReport(settlement: SettlementDto): Promise<void> {
    // Reentrancy guard: mirrors deposit-page.component.ts's reprintDepositSlip/reprintLabels — a
    // print job costs a physical sheet that can't be recalled, so a double-click must never queue
    // twice. Global (not per-row): the backend print queue is single-threaded per printer anyway
    // (PrintQueueService), so every print button is disabled while any one report is in flight.
    if (this.anyPrintInFlight()) {
      return;
    }
    this.printingReportForSellerId.set(settlement.sellerId);
    try {
      await firstValueFrom(this.settlementService.printReport(settlement.sellerId));
      this.toast.showSuccess(this.translate.instant('settlement.success.printReport'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('settlement.error.printerUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('settlement.error.printReport'));
      }
    } finally {
      this.printingReportForSellerId.set(null);
    }
  }

  /**
   * Story 5.8 — best-effort auto-print of the sales report right after a successful settle, when
   * the "Imprimer le bilan de vente" box was left checked. Pattern: pos-page.autoPrintInvoice
   * (story 4.7). Never rethrows, never touches `submitting`, never reverts the settlement — an
   * error here only raises a toast. Reuses `printingReportForSellerId` so a manual re-print stays
   * blocked (via `anyPrintInFlight`) while it runs.
   */
  private async autoPrintReport(sellerId: number): Promise<void> {
    this.printingReportForSellerId.set(sellerId);
    try {
      await firstValueFrom(this.settlementService.printReport(sellerId));
      this.toast.showSuccess(this.translate.instant('settlement.success.printReport'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('settlement.error.printerUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('settlement.error.printReport'));
      }
    } finally {
      this.printingReportForSellerId.set(null);
    }
  }

  async printAllReports(): Promise<void> {
    if (this.anyPrintInFlight()) {
      return;
    }
    this.printingAll.set(true);
    try {
      const result = await firstValueFrom(this.settlementService.printAllReports(this.statusFilter()));
      if (result.failedCount > 0) {
        this.toast.showError(this.translate.instant('settlement.error.printAllPartial', { count: result.failedCount }), {
          path: '/admin/printers/queue',
          label: this.translate.instant('settlement.error.printAllPartialLink'),
        });
      } else {
        this.toast.showSuccess(this.translate.instant('settlement.success.printAll', { count: result.succeededCount }));
      }
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('settlement.error.printerUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('settlement.error.printAll'));
      }
    } finally {
      this.printingAll.set(false);
    }
  }

  /**
   * @param silent when true, a background refresh triggered by another terminal's action or by
   * the catch-up reload after a local action: never touches the skeleton (`isLoading`) and never
   * raises the full-screen `error()` banner. A failed silent reload keeps whatever is currently
   * on screen (e.g. racing an edition closure that flips GET /api/settlements to 422) — the list
   * is only ever replaced on success.
   */
  private loadSettlementsSeq = 0;

  private async loadSettlements(silent = false): Promise<void> {
    // Monotonic guard: ngOnInit, the catch-up reload in confirmSettle/confirmUnclaimed's finally
    // and the SSE-triggered reload can all be in flight at once. Only the most recent call is
    // allowed to write settlements()/error(), so an older response resolving last can never
    // overwrite fresher rows.
    const seq = ++this.loadSettlementsSeq;
    if (!silent) {
      this.isLoading.set(true);
      this.error.set(null);
    }
    try {
      const settlements = await firstValueFrom(this.settlementService.getSettlements());
      if (seq !== this.loadSettlementsSeq) {
        return;
      }
      this.settlements.set(settlements);
      // Fresh data is on screen — drop any error banner left by an earlier failed load, otherwise
      // the template's `!isLoading() && !error()` guard keeps the list hidden behind it.
      this.error.set(null);
    } catch (err: unknown) {
      if (seq !== this.loadSettlementsSeq) {
        return;
      }
      if (silent) {
        console.debug('Silent settlement reload failed; keeping the current list');
        return;
      }
      const errorType = err instanceof HttpErrorResponse ? extractErrorType(err) : undefined;
      this.error.set(errorType?.endsWith('/no-active-edition') ? 'settlement.error.noActiveEdition' : 'settlement.error.load');
    } finally {
      if (!silent) {
        this.isLoading.set(false);
      }
    }
  }

  private applyUpdate(updated: SettlementDto): void {
    this.settlements.update((list) => list.map((s) => (s.sellerId === updated.sellerId ? updated : s)));
  }
}
