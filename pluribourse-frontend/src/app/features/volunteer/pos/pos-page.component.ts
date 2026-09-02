import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { Basket } from '../../../models/pos.model';
import { CurrentEditionService } from '../../../services/current-edition.service';
import { PosService } from '../../../services/pos.service';
import { SseService } from '../../../services/sse.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { extractConflictingItems, extractErrorType } from '../../../shared/http-error.util';
import { PaymentDialogService } from './payment-dialog.service';
import { ScannerInputComponent } from './scanner-input.component';

interface ScanIssue {
  message: string;
  variant: 'warning' | 'error';
}

@Component({
  selector: 'app-pos-page',
  standalone: true,
  imports: [TranslatePipe, MatButtonModule, MatIconModule, NotificationInlineComponent, ScannerInputComponent],
  templateUrl: './pos-page.component.html',
  styleUrl: './pos-page.component.scss',
})
export class PosPageComponent implements OnInit {
  private readonly posService = inject(PosService);
  private readonly sseService = inject(SseService);
  private readonly paymentDialogService = inject(PaymentDialogService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly currency = computed(() => this.currentEditionService.currentEdition()?.currency);

  // Story 4.2: the basket is now persisted server-side (NFR-006) — no longer a client-only signal.
  readonly basket = signal<Basket | null>(null);
  readonly lastScanIssue = signal<ScanIssue | null>(null);
  // Story 4.6: set once by onBasketCancelled() and never cleared until a full page reload —
  // the scanner has no re-enable path in JS by design (epics.md AC 2). Also read as a general
  // "ignore this stale in-flight response" guard well beyond the scanner itself (removeItem,
  // removeLot, openPaymentDialog, loadBasket) — named after the event, not just the input.
  readonly basketCancelled = signal(false);

  // Serializes onScan() calls: without this, two scans fired before the first HTTP response
  // resolves could both be in flight against the same basket at once (Story 4.1 review finding,
  // still relevant here since addItem() is now the network call being raced).
  private scanInFlight = false;
  // Same reentrancy concern as scanInFlight, for the remove/validate actions: a double-click
  // before the first response resolves would otherwise fire a duplicate request. Exposed as a
  // signal (not a plain field, unlike scanInFlight/validateInFlight) so the template can disable
  // the remove buttons while a removal is in flight.
  readonly removeInFlight = signal(false);
  private validateInFlight = false;

  ngOnInit(): void {
    void this.loadBasket();

    this.sseService.basketCancelled().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(() => this.onBasketCancelled());
  }

  async onScan(barcode: string): Promise<void> {
    const currentBasket = this.basket();
    if (this.scanInFlight || !currentBasket) {
      return;
    }
    this.scanInFlight = true;
    try {
      const previousItemIds = new Set(currentBasket.items.map(item => item.itemId));
      const updated = await firstValueFrom(this.posService.addItem(currentBasket.id, barcode));
      if (this.basketCancelled()) {
        // Story 4.6: a basket-cancelled event arrived while this scan was in flight — do not
        // resurrect the basket or a scan message next to the persistent cancellation toast.
        return;
      }
      this.basket.set(updated);
      const addedItem = updated.items.find(item => !previousItemIds.has(item.itemId));
      if (addedItem?.incomplete) {
        const comment = addedItem.comment || this.translate.instant('volunteer.deposit.item.list.noComment');
        this.lastScanIssue.set({
          message: this.translate.instant('volunteer.pos.warning.incomplete', { comment }),
          variant: 'warning',
        });
      } else {
        this.lastScanIssue.set(null);
      }
    } catch (err: unknown) {
      if (this.basketCancelled()) {
        return;
      }
      this.handleScanError(err);
    } finally {
      this.scanInFlight = false;
    }
  }

  async removeItem(itemId: number): Promise<void> {
    const currentBasket = this.basket();
    if (this.removeInFlight() || !currentBasket) {
      return;
    }
    this.removeInFlight.set(true);
    try {
      const updated = await firstValueFrom(this.posService.removeItem(currentBasket.id, itemId));
      if (this.basketCancelled()) {
        return;
      }
      this.basket.set(updated);
      // The "Article incomplet" warning (onScan) refers to the item just removed — leaving it
      // displayed after removal would be stale/misleading.
      this.lastScanIssue.set(null);
    } catch {
      if (this.basketCancelled()) {
        return;
      }
      this.toast.showError(this.translate.instant('volunteer.pos.error.generic'));
    } finally {
      this.removeInFlight.set(false);
    }
  }

  async removeLot(lotId: number): Promise<void> {
    const currentBasket = this.basket();
    if (this.removeInFlight() || !currentBasket) {
      return;
    }
    this.removeInFlight.set(true);
    try {
      const updated = await firstValueFrom(this.posService.removeLot(currentBasket.id, lotId));
      if (this.basketCancelled()) {
        return;
      }
      this.basket.set(updated);
      this.lastScanIssue.set(null);
    } catch {
      if (this.basketCancelled()) {
        return;
      }
      this.toast.showError(this.translate.instant('volunteer.pos.error.generic'));
    } finally {
      this.removeInFlight.set(false);
    }
  }

  private onBasketCancelled(): void {
    if (this.basket() === null) {
      // AC 3 — theoretical case: the event arrives before loadBasket() has resolved.
      return;
    }
    this.basket.set(null);
    this.lastScanIssue.set(null);
    this.basketCancelled.set(true);
    this.toast.showError(this.translate.instant('volunteer.pos.error.phaseChanged'));
  }

  async openPaymentDialog(): Promise<void> {
    const currentBasket = this.basket();
    if (this.validateInFlight || !currentBasket || currentBasket.items.length === 0) {
      return;
    }
    const result = await firstValueFrom(
      this.paymentDialogService.open({ items: currentBasket.items, total: currentBasket.total, currency: this.currency() })
    );
    if (!result) {
      return;
    }
    this.validateInFlight = true;
    try {
      const sale = await firstValueFrom(this.posService.validate(currentBasket.id, result.request));
      this.lastScanIssue.set(null);
      // The validated basket is deleted server-side — reload to pick up the fresh empty one
      // created for the next transaction (AC4), rather than just clearing items locally.
      await this.loadBasket();
      if (result.printInvoice) {
        // Story 4.7 AC 3: best-effort and decoupled — not awaited before the fresh basket is
        // loaded, and a print failure never invalidates the sale.
        void this.autoPrintInvoice(sale.id);
      }
    } catch (err: unknown) {
      if (this.basketCancelled()) {
        // Story 4.6 review: a basket-cancelled event arrived while validate() was in flight —
        // the basket was already deleted server-side by that same transition (Story 2.8), so
        // this request 404s harmlessly; don't let its generic error toast overwrite the
        // persistent cancellation toast already shown by onBasketCancelled().
        return;
      }
      this.handleValidationError(err);
    } finally {
      this.validateInFlight = false;
    }
  }

  /**
   * Story 4.7 — automatic invoice print triggered by a validated sale when the cashier left the
   * "Imprimer la facture" box checked. Best-effort: its own error handling, its own toast, and it
   * never blocks or reverts anything (the sale is already done, the fresh basket already loaded).
   */
  private async autoPrintInvoice(saleId: number): Promise<void> {
    try {
      await firstValueFrom(this.posService.printInvoice(saleId));
      if (this.basketCancelled()) {
        // A basket-cancelled event landed while the print request was in flight. Story 4.6 kept
        // the manual 30 s button working after a cancellation, but here the print is a side
        // effect of validation, not a deliberate post-cancellation action — don't overwrite the
        // persistent cancellation toast with a print success toast.
        return;
      }
      this.toast.showSuccess(this.translate.instant('volunteer.pos.invoice.success'));
    } catch (err: unknown) {
      if (this.basketCancelled()) {
        return;
      }
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('volunteer.pos.invoice.error.a4PrinterUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('volunteer.pos.invoice.error.generic'));
      }
    }
  }

  private async loadBasket(): Promise<void> {
    try {
      const basket = await firstValueFrom(this.posService.getCurrentBasket());
      if (this.basketCancelled()) {
        // Story 4.6 review: a basket-cancelled event arrived while this reload was in flight —
        // called both from ngOnInit() (harmless, basketCancelled() is always false there) and
        // from openPaymentDialog() after a successful validate(); don't resurrect a basket or
        // overwrite the persistent cancellation toast with a stale generic error.
        return;
      }
      this.basket.set(basket);
    } catch (err: unknown) {
      if (this.basketCancelled()) {
        return;
      }
      this.handleScanError(err);
    }
  }

  private handleValidationError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const type = extractErrorType(err);
      if (type?.endsWith('/basket-validation-conflict')) {
        const names = (extractConflictingItems(err) ?? []).map(item => item.name).join(', ');
        // The conflicting items must be removed manually by the volunteer — the basket is left
        // untouched (architecture § Concurrence POS: no automatic conflict resolution).
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.error.conflict', { names }), variant: 'error' });
        return;
      }
      if (type?.endsWith('/lot-already-sold')) {
        // FR-109 (story 5.8): another member of this lot was sold on another terminal between the
        // scan and this validation. Manual resolution — the basket is left untouched.
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.error.lotAlreadySold'), variant: 'error' });
        return;
      }
    }
    this.toast.showError(this.translate.instant('volunteer.pos.error.generic'));
  }

  private handleScanError(err: unknown): void {
    if (err instanceof HttpErrorResponse) {
      const type = extractErrorType(err);
      if (type?.endsWith('/item-already-sold')) {
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.error.alreadySold'), variant: 'error' });
        return;
      }
      if (type?.endsWith('/lot-already-sold')) {
        // FR-109 (story 5.8): a sibling of this item's lot is already sold — the lot is done.
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.error.lotAlreadySold'), variant: 'error' });
        return;
      }
      if (type?.endsWith('/item-not-found')) {
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.error.notFound'), variant: 'error' });
        return;
      }
      if (type?.endsWith('/item-already-in-basket')) {
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.warning.alreadyInBasket'), variant: 'warning' });
        return;
      }
      if (type?.endsWith('/no-active-edition')) {
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.deposit.error.noActiveEdition'), variant: 'error' });
        return;
      }
    }
    this.toast.showError(this.translate.instant('volunteer.pos.error.generic'));
  }
}
