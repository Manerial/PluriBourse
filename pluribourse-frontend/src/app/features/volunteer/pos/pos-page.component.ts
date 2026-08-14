import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { Basket, Sale } from '../../../models/pos.model';
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

// EXPERIENCE.md § Panier POS — état post-validation : the invoice button stays visible for exactly
// this long after a successful sale, then disappears on its own (no user action required).
const INVOICE_BUTTON_VISIBLE_MS = 30000;

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
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  // Story 4.2: the basket is now persisted server-side (NFR-006) — no longer a client-only signal.
  readonly basket = signal<Basket | null>(null);
  readonly lastScanIssue = signal<ScanIssue | null>(null);
  // Story 4.5: the most recently validated sale, whose invoice can be (re)printed on demand while
  // this signal is set. Independent of basket/scan state — never cleared by onScan()/addItem()
  // (AC 5): the scanner stays usable for a new transaction while the print button is still shown.
  readonly lastSale = signal<Sale | null>(null);
  readonly printingInvoice = signal(false);
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
  // before the first response resolves would otherwise fire a duplicate request.
  private removeInFlight = false;
  private validateInFlight = false;
  private invoiceButtonTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.destroyRef.onDestroy(() => clearTimeout(this.invoiceButtonTimer));
  }

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
        this.lastScanIssue.set({
          message: this.translate.instant('volunteer.pos.warning.incomplete', { comment: addedItem.comment }),
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
    if (this.removeInFlight || !currentBasket) {
      return;
    }
    this.removeInFlight = true;
    try {
      const updated = await firstValueFrom(this.posService.removeItem(currentBasket.id, itemId));
      if (this.basketCancelled()) {
        return;
      }
      this.basket.set(updated);
    } catch {
      if (this.basketCancelled()) {
        return;
      }
      this.toast.showError(this.translate.instant('volunteer.pos.error.generic'));
    } finally {
      this.removeInFlight = false;
    }
  }

  async removeLot(lotId: number): Promise<void> {
    const currentBasket = this.basket();
    if (this.removeInFlight || !currentBasket) {
      return;
    }
    this.removeInFlight = true;
    try {
      const updated = await firstValueFrom(this.posService.removeLot(currentBasket.id, lotId));
      if (this.basketCancelled()) {
        return;
      }
      this.basket.set(updated);
    } catch {
      if (this.basketCancelled()) {
        return;
      }
      this.toast.showError(this.translate.instant('volunteer.pos.error.generic'));
    } finally {
      this.removeInFlight = false;
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
      this.paymentDialogService.open({ items: currentBasket.items, total: currentBasket.total })
    );
    if (!result) {
      return;
    }
    this.validateInFlight = true;
    try {
      const sale = await firstValueFrom(this.posService.validate(currentBasket.id, result));
      this.lastScanIssue.set(null);
      this.showInvoiceButton(sale);
      // The validated basket is deleted server-side — reload to pick up the fresh empty one
      // created for the next transaction (AC4), rather than just clearing items locally.
      await this.loadBasket();
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

  async printInvoice(): Promise<void> {
    const sale = this.lastSale();
    if (this.printingInvoice() || !sale) {
      return;
    }
    this.printingInvoice.set(true);
    try {
      await firstValueFrom(this.posService.printInvoice(sale.id));
      this.toast.showSuccess(this.translate.instant('volunteer.pos.invoice.success'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('volunteer.pos.invoice.error.a4PrinterUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('volunteer.pos.invoice.error.generic'));
      }
    } finally {
      this.printingInvoice.set(false);
    }
  }

  private showInvoiceButton(sale: Sale): void {
    clearTimeout(this.invoiceButtonTimer);
    this.lastSale.set(sale);
    this.invoiceButtonTimer = setTimeout(() => this.lastSale.set(null), INVOICE_BUTTON_VISIBLE_MS);
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
