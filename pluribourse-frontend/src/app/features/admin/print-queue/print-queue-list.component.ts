import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, Observable } from 'rxjs';
import { PrinterStatus } from '../../../models/printer-status.model';
import { PrintQueueService } from '../../../services/print-queue.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';

@Component({
  selector: 'app-print-queue-list',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe, SkeletonRowComponent, EmptyStateComponent, NotificationInlineComponent],
  templateUrl: './print-queue-list.component.html',
  styleUrl: './print-queue-list.component.scss',
})
export class PrintQueueListComponent implements OnInit {
  private readonly printQueueService = inject(PrintQueueService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly statuses = signal<PrinterStatus[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  private readonly printersWithActionInProgress = signal<ReadonlySet<number>>(new Set());

  async ngOnInit(): Promise<void> {
    await this.load(true);
  }

  async refresh(): Promise<void> {
    await this.load(true);
  }

  isActionInProgress(printerId: number): boolean {
    return this.printersWithActionInProgress().has(printerId);
  }

  connectionState(printer: PrinterStatus): 'connected' | 'jobError' | 'disconnected' {
    if (printer.connected) {
      return 'connected';
    }
    // A suspended queue means a job failed on an otherwise-reachable printer (e.g. paper jam) —
    // distinct from a printer that was never reachable at all, so it gets its own visual state
    // instead of being shown as "Hors ligne" (see story 3.7 review decision).
    return printer.canRetry ? 'jobError' : 'disconnected';
  }

  async resume(printer: PrinterStatus): Promise<void> {
    await this.runAction(printer.id, this.printQueueService.resumeQueue(printer.id), 'resume');
  }

  async discard(printer: PrinterStatus): Promise<void> {
    await this.runAction(printer.id, this.printQueueService.discardFailedJob(printer.id), 'discard');
  }

  // showLoadingState is false for the reload triggered right after a resume/discard action: that
  // reload must not blank the already-rendered grid behind the skeleton, and if it fails, the
  // previously fetched cards must stay visible instead of being replaced by a bare error banner.
  private async load(showLoadingState: boolean): Promise<void> {
    if (showLoadingState) {
      this.isLoading.set(true);
    }
    this.error.set(null);
    try {
      this.statuses.set(await firstValueFrom(this.printQueueService.getStatuses()));
    } catch {
      this.error.set('admin.printQueue.error.load');
    } finally {
      if (showLoadingState) {
        this.isLoading.set(false);
      }
    }
  }

  private async runAction(printerId: number, action$: Observable<void>, key: 'resume' | 'discard'): Promise<void> {
    this.markActionInProgress(printerId, true);
    try {
      await firstValueFrom(action$);
      this.toast.showSuccess(this.translate.instant(`admin.printQueue.success.${key}`));
    } catch {
      this.toast.showError(this.translate.instant(`admin.printQueue.error.${key}`));
    } finally {
      // Reload even on failure: a 422 here means another admin session already changed this
      // printer's state (e.g. resumed/discarded it first) — the card must reflect that, not the
      // stale state that led to this now-outdated action being offered.
      await this.load(false);
      this.markActionInProgress(printerId, false);
    }
  }

  private markActionInProgress(printerId: number, inProgress: boolean): void {
    this.printersWithActionInProgress.update(current => {
      const next = new Set(current);
      if (inProgress) {
        next.add(printerId);
      } else {
        next.delete(printerId);
      }
      return next;
    });
  }
}
