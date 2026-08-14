import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import Big from 'big.js';
import { firstValueFrom } from 'rxjs';
import { SettlementDto } from '../../models/settlement.model';
import { SettlementService } from '../../services/settlement.service';
import { AuthService } from '../../services/auth.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../shared/components/confirm-dialog/confirm-dialog.service';
import { SkeletonRowComponent } from '../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../shared/components/notification-inline/notification-inline.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { extractErrorType } from '../../shared/http-error.util';

type StatusFilter = 'all' | 'unsettled' | 'settled';

@Component({
  selector: 'app-settlement-list',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
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

  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');

  readonly settlements = signal<SettlementDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly statusFilter = signal<StatusFilter>('unsettled');
  readonly filteredSettlements = computed(() => {
    const filter = this.statusFilter();
    return this.settlements().filter((s) =>
      filter === 'all' ? true : filter === 'unsettled' ? s.status === 'UNSETTLED' : s.status !== 'UNSETTLED'
    );
  });

  readonly openSettleFormForSellerId = signal<number | null>(null);
  readonly settleAmount = signal<number | null>(null);

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
    });
  });

  async ngOnInit(): Promise<void> {
    await this.loadSettlements();
  }

  setStatusFilter(filter: StatusFilter): void {
    this.statusFilter.set(filter);
  }

  openSettleForm(settlement: SettlementDto): void {
    this.openSettleFormForSellerId.set(settlement.sellerId);
    this.settleAmount.set(settlement.amountDue);
  }

  closeSettleForm(): void {
    this.openSettleFormForSellerId.set(null);
    this.settleAmount.set(null);
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
      this.closeSettleForm();
    } catch {
      // Server is the actual source of truth (never trust the client alone, cf. PosBasketService)
      // — a 422 here is unlikely since the amount is already blocked client-side, but still handled.
      this.toast.showError(this.translate.instant('settlement.error.settle'));
    } finally {
      this.submitting.set(false);
    }
  }

  async confirmUnclaimed(settlement: SettlementDto): Promise<void> {
    const confirmed = await firstValueFrom(
      this.confirmDialog.open({
        title: this.translate.instant('settlement.unclaimedDialog.title'),
        description: this.translate.instant('settlement.unclaimedDialog.description', {
          amount: settlement.amountDue.toFixed(2),
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
    } catch {
      this.toast.showError(this.translate.instant('settlement.error.settle'));
    } finally {
      this.submitting.set(false);
    }
  }

  private async loadSettlements(): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      const settlements = await firstValueFrom(this.settlementService.getSettlements());
      this.settlements.set(settlements);
    } catch (err: unknown) {
      const errorType = err instanceof HttpErrorResponse ? extractErrorType(err) : undefined;
      this.error.set(errorType?.endsWith('/no-active-edition') ? 'settlement.error.noActiveEdition' : 'settlement.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  private applyUpdate(updated: SettlementDto): void {
    this.settlements.update((list) => list.map((s) => (s.sellerId === updated.sellerId ? updated : s)));
  }
}
