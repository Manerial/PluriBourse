import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { ScanResult } from '../../../models/pos.model';
import { PosService } from '../../../services/pos.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { extractErrorType } from '../../../shared/http-error.util';
import { ScannerInputComponent } from './scanner-input.component';

interface ScanIssue {
  message: string;
  variant: 'warning' | 'error';
}

@Component({
  selector: 'app-pos-page',
  standalone: true,
  imports: [TranslatePipe, NotificationInlineComponent, ScannerInputComponent],
  templateUrl: './pos-page.component.html',
  styleUrl: './pos-page.component.scss',
})
export class PosPageComponent {
  private readonly posService = inject(PosService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  // Client-side only for this story — no Basket entity exists yet (Story 4.2 introduces it).
  readonly basket = signal<ScanResult[]>([]);
  readonly lastScanIssue = signal<ScanIssue | null>(null);

  // Serializes onScan() calls: without this, two scans of the same barcode fired before the
  // first HTTP response resolves could both read basket() before either had written to it,
  // both passing the already-in-basket check below and duplicating the item anyway.
  private scanInFlight = false;

  async onScan(barcode: string): Promise<void> {
    if (this.scanInFlight) {
      return;
    }
    this.scanInFlight = true;
    try {
      const result = await firstValueFrom(this.posService.scan(barcode));
      if (this.basket().some(item => item.itemId === result.itemId)) {
        // Nothing marks an item sold until Story 4.2's payment validation, so scanning the same
        // barcode twice (double-read, accidental re-scan) would otherwise silently duplicate it.
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.pos.warning.alreadyInBasket'), variant: 'warning' });
        return;
      }
      this.basket.update(items => [...items, result]);
      if (result.incomplete) {
        this.lastScanIssue.set({
          message: this.translate.instant('volunteer.pos.warning.incomplete', { comment: result.comment }),
          variant: 'warning',
        });
      } else {
        this.lastScanIssue.set(null);
      }
    } catch (err: unknown) {
      this.handleScanError(err);
    } finally {
      this.scanInFlight = false;
    }
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
      if (type?.endsWith('/no-active-edition')) {
        this.lastScanIssue.set({ message: this.translate.instant('volunteer.deposit.error.noActiveEdition'), variant: 'error' });
        return;
      }
    }
    this.toast.showError(this.translate.instant('volunteer.pos.error.generic'));
  }
}
