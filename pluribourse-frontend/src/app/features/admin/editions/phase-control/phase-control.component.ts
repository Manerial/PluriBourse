import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { firstValueFrom } from 'rxjs';
import Big from 'big.js';
import { EditionDto, PhaseType } from '../../../../models/edition.model';
import { EditionService } from '../../../../services/edition.service';
import { SettlementService } from '../../../../services/settlement.service';
import { ReportService } from '../../../../services/report.service';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../../shared/components/notification-inline/notification-inline.component';
import { DialogShellComponent } from '../../../../shared/components/dialog-shell/dialog-shell.component';
import { extractErrorType } from '../../../../shared/http-error.util';
import { ALL_PHASES } from '../../../../models/active-phase.enum';

export interface PhaseControlDialogData {
  editionId: number;
}

@Component({
  selector: 'app-phase-control',
  standalone: true,
  imports: [TranslatePipe, MatButtonModule, MatIconModule, SkeletonRowComponent, NotificationInlineComponent, DialogShellComponent],
  templateUrl: './phase-control.component.html',
  styleUrl: './phase-control.component.scss',
})
export class PhaseControlComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly settlementService = inject(SettlementService);
  private readonly reportService = inject(ReportService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly destroyRef = inject(DestroyRef);

  readonly dialogRef = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<PhaseControlDialogData>(DIALOG_DATA);

  readonly edition = signal<EditionDto | null>(null);
  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    if (!this.data.editionId || this.data.editionId <= 0) {
      this.error.set('phase.control.error.load');
      return;
    }
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.edition.set(await firstValueFrom(this.editionService.getById(this.data.editionId)));
    } catch {
      this.error.set('phase.control.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  canAdvance(): boolean {
    const e = this.edition();
    if (!e) {
      return false;
    }
    // POST_SALE now goes through the dedicated close flow below, not the generic advance button.
    return e.phase !== 'CLOSED' && e.phase !== 'POST_SALE';
  }

  canClose(): boolean {
    const e = this.edition();
    return !!e && e.phase === 'POST_SALE';
  }

  canArchive(): boolean {
    const e = this.edition();
    return !!e && e.phase === 'CLOSED' && !e.archived && !!e.hasItems;
  }

  canRollback(): boolean {
    const e = this.edition();
    if (!e) {
      return false;
    }
    if (e.phase === 'PREPARATION') {
      return false;
    }
    return !(e.phase === 'CLOSED' && e.archived);
  }

  nextPhase(): PhaseType | null {
    const current = this.edition()?.phase;
    if (!current) {
      return null;
    }
    const idx = ALL_PHASES.indexOf(current);
    return idx < ALL_PHASES.length - 1 ? ALL_PHASES[idx + 1] : null;
  }

  prevPhase(): PhaseType | null {
    const current = this.edition()?.phase;
    if (!current) {
      return null;
    }
    const idx = ALL_PHASES.indexOf(current);
    return idx > 0 ? ALL_PHASES[idx - 1] : null;
  }

  confirmAdvance(): void {
    const e = this.edition();
    if (!e || !this.canAdvance() || this.isSubmitting()) {
      return;
    }
    const next = this.nextPhase()!;
    const nextLabel = this.translate.instant('edition.phase.' + next);
    this.isSubmitting.set(true);
    this.confirmDialog.open({
      title: this.translate.instant('phase.advance.dialog.title', { nextPhase: nextLabel }),
      description: this.translate.instant('phase.advance.dialog.description.' + e.phase),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) {
        this.isSubmitting.set(false);
        return;
      }
      try {
        this.edition.set(await firstValueFrom(this.editionService.advancePhase(e.id)));
        this.toast.showSuccess(this.translate.instant('phase.advance.success'));
        this.dialogRef.close();
      } catch (err: unknown) {
        if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/no-categories-configured')) {
          this.toast.showError(this.translate.instant('phase.advance.error.noCategoriesConfigured'));
        } else {
          this.toast.showError(this.translate.instant('phase.advance.error.generic'));
        }
      } finally {
        this.isSubmitting.set(false);
      }
    });
  }

  confirmRollback(): void {
    const e = this.edition();
    if (!e || !this.canRollback() || this.isSubmitting()) {
      return;
    }
    const prev = this.prevPhase()!;
    const prevLabel = this.translate.instant('edition.phase.' + prev);
    this.isSubmitting.set(true);
    this.confirmDialog.open({
      title: this.translate.instant('phase.rollback.dialog.title', { prevPhase: prevLabel }),
      description: this.translate.instant('phase.rollback.dialog.description.' + e.phase),
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) {
        this.isSubmitting.set(false);
        return;
      }
      try {
        this.edition.set(await firstValueFrom(this.editionService.rollbackPhase(e.id)));
        this.toast.showSuccess(this.translate.instant('phase.rollback.success'));
        this.dialogRef.close();
      } catch {
        this.toast.showError(this.translate.instant('phase.rollback.error'));
      } finally {
        this.isSubmitting.set(false);
      }
    });
  }

  async confirmClose(): Promise<void> {
    const e = this.edition();
    if (!e || !this.canClose() || this.isSubmitting()) {
      return;
    }
    this.isSubmitting.set(true);

    let description: string;
    try {
      const settlements = await firstValueFrom(this.settlementService.getSettlements());
      const unsettled = settlements.filter((s) => s.status === 'UNSETTLED');
      if (unsettled.length > 0) {
        const total = unsettled.reduce((sum, s) => sum.plus(s.amountDue), new Big(0));
        description = this.translate.instant('phase.close.dialog.warningUnsettled', {
          count: unsettled.length,
          amount: total.toFixed(2),
        });
      } else {
        description = this.translate.instant('phase.close.dialog.description');
      }
    } catch {
      this.toast.showError(this.translate.instant('phase.close.error.generic'));
      this.isSubmitting.set(false);
      return;
    }

    this.confirmDialog.open({
      title: this.translate.instant('phase.close.dialog.title'),
      description,
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) {
        this.isSubmitting.set(false);
        return;
      }
      try {
        this.edition.set(await firstValueFrom(this.editionService.closeEdition(e.id)));
        this.toast.showSuccess(this.translate.instant('phase.close.success'));
        this.dialogRef.close();
      } catch {
        this.toast.showError(this.translate.instant('phase.close.error.generic'));
        this.isSubmitting.set(false);
        return;
      }
      // Best-effort (AC 4): the closure above already succeeded and the dialog is already closed —
      // a printing failure here is reported separately and never undoes the closure.
      try {
        await firstValueFrom(this.reportService.printEditionReportClosure(e.id));
      } catch {
        this.toast.showError(this.translate.instant('phase.close.error.printReport'));
      } finally {
        this.isSubmitting.set(false);
      }
    });
  }

  confirmArchive(): void {
    const e = this.edition();
    if (!e || !this.canArchive() || this.isSubmitting()) {
      return;
    }
    this.isSubmitting.set(true);
    this.confirmDialog.open({
      title: this.translate.instant('phase.archive.dialog.title'),
      description: this.translate.instant('phase.archive.dialog.description'),
      confirmVariant: 'error',
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) {
        this.isSubmitting.set(false);
        return;
      }
      try {
        this.edition.set(await firstValueFrom(this.editionService.archiveEdition(e.id)));
        this.toast.showSuccess(this.translate.instant('phase.archive.success'));
        this.dialogRef.close();
      } catch {
        this.toast.showError(this.translate.instant('phase.archive.error'));
      } finally {
        this.isSubmitting.set(false);
      }
    });
  }
}
