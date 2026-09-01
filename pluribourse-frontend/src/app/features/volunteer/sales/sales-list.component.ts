import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { SaleListFilter, SaleListItem } from '../../../models/pos.model';
import { PosService } from '../../../services/pos.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { extractErrorType } from '../../../shared/http-error.util';

const DEFAULT_PAGE_SIZE = 50;

/**
 * Sales list screen (story 4.7, FR-108). Structural copy of {@code ItemCatalogComponent}: the
 * server keeps the in-memory filter pattern, this component keeps a {@code requestSequence} guard
 * against out-of-order responses, {@code MatSortModule} + {@code matSortDisableClear}, and the
 * shared empty/loading/error states. The {@code mat-sort-header} ids MUST match the backend
 * {@code ALLOWED_SORT_FIELDS} exactly: {@code soldAt}, {@code user.username}, {@code paymentMethod},
 * {@code total}.
 */
@Component({
  selector: 'app-sales-list',
  standalone: true,
  imports: [
    DatePipe,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatPaginatorModule,
    MatSelectModule,
    MatSortModule,
    TranslatePipe,
    EmptyStateComponent,
    NotificationInlineComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './sales-list.component.html',
  styleUrl: './sales-list.component.scss',
})
export class SalesListComponent implements OnInit {
  private readonly posService = inject(PosService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly sales = signal<SaleListItem[]>([]);
  readonly totalElements = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = DEFAULT_PAGE_SIZE;
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);

  readonly cashiers = signal<string[]>([]);

  // Bound to <input type="datetime-local"> — values look like "2026-06-12T14:30" (no seconds);
  // Spring's @DateTimeFormat(iso = DATE_TIME) parses that as-is.
  readonly dateFromFilter = signal('');
  readonly dateToFilter = signal('');
  readonly cashierFilter = signal<string | null>(null);

  readonly sortField = signal<string | undefined>(undefined);
  readonly sortDirection = signal<'asc' | 'desc' | ''>('');

  // AC 14 — one reprint in flight at a time, keyed by row so its button can show a disabled state.
  readonly reprintInFlightId = signal<number | null>(null);

  private requestSequence = 0;

  async ngOnInit(): Promise<void> {
    await this.loadCashiers();
    await this.loadPage(0);
  }

  async onDateFromChange(value: string): Promise<void> {
    this.dateFromFilter.set(value);
    await this.loadPage(0);
  }

  async onDateToChange(value: string): Promise<void> {
    this.dateToFilter.set(value);
    await this.loadPage(0);
  }

  async onCashierChange(value: string | null): Promise<void> {
    this.cashierFilter.set(value);
    await this.loadPage(0);
  }

  async onPageChange(event: PageEvent): Promise<void> {
    await this.loadPage(event.pageIndex);
  }

  async onSortChange(sort: Sort): Promise<void> {
    this.sortField.set(sort.direction ? sort.active : undefined);
    this.sortDirection.set(sort.direction);
    await this.loadPage(0);
  }

  async reprint(saleId: number): Promise<void> {
    if (this.reprintInFlightId() !== null) {
      return;
    }
    this.reprintInFlightId.set(saleId);
    try {
      await firstValueFrom(this.posService.printInvoice(saleId));
      this.toast.showSuccess(this.translate.instant('volunteer.pos.invoice.success'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('volunteer.pos.invoice.error.a4PrinterUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('volunteer.pos.invoice.error.generic'));
      }
    } finally {
      this.reprintInFlightId.set(null);
    }
  }

  private async loadCashiers(): Promise<void> {
    try {
      this.cashiers.set(await firstValueFrom(this.posService.listCashiers()));
    } catch {
      this.cashiers.set([]);
    }
  }

  private async loadPage(page: number): Promise<void> {
    // Same rationale as ItemCatalogComponent.loadPage: rapid filter/sort/page changes fire
    // overlapping requests with no ordering guarantee — only the newest response is applied.
    const requestId = ++this.requestSequence;
    this.isLoading.set(true);
    this.error.set(null);
    const filter: SaleListFilter = {
      dateFrom: this.dateFromFilter() || undefined,
      dateTo: this.dateToFilter() || undefined,
      cashier: this.cashierFilter() ?? undefined,
      page,
      size: this.pageSize,
      sort: this.buildSort(),
    };
    try {
      const result = await firstValueFrom(this.posService.listSales(filter));
      if (requestId !== this.requestSequence) {
        return;
      }
      this.sales.set(result.page.content);
      this.totalElements.set(result.page.totalElements);
      this.pageIndex.set(page);
    } catch (err: unknown) {
      if (requestId !== this.requestSequence) {
        return;
      }
      const errorType = err instanceof HttpErrorResponse ? extractErrorType(err) : undefined;
      this.error.set(errorType?.endsWith('/no-active-edition')
        ? 'volunteer.sales.error.noActiveEdition'
        : 'volunteer.sales.error.load');
    } finally {
      if (requestId === this.requestSequence) {
        this.isLoading.set(false);
      }
    }
  }

  private buildSort(): string | undefined {
    const field = this.sortField();
    const direction = this.sortDirection();
    return field && direction ? `${field},${direction}` : undefined;
  }
}
