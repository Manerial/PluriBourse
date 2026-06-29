import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { EditionDto, PhaseType } from '../../../../models/edition.model';
import { EditionService } from '../../../../services/edition.service';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../../shared/components/notification-inline/notification-inline.component';

const PHASE_ORDER: PhaseType[] = ['PREPARATION', 'DEPOSIT', 'SALE', 'POST_SALE', 'CLOSED'];

@Component({
  selector: 'app-phase-control',
  standalone: true,
  imports: [TranslatePipe, RouterLink, SkeletonRowComponent, NotificationInlineComponent],
  templateUrl: './phase-control.component.html',
  styleUrl: './phase-control.component.scss',
})
export class PhaseControlComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);

  readonly edition = signal<EditionDto | null>(null);
  readonly isLoading = signal(false);
  readonly isSubmitting = signal(false);
  readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    const rawId = this.route.snapshot.paramMap.get('id');
    const id = Number(rawId);
    if (!rawId || isNaN(id) || id <= 0) {
      this.error.set('phase.control.error.load');
      return;
    }
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.edition.set(await firstValueFrom(this.editionService.getById(id)));
    } catch {
      this.error.set('phase.control.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  canAdvance(): boolean {
    const e = this.edition();
    if (!e) { return false; }
    return e.phase !== 'CLOSED';
  }

  canRollback(): boolean {
    const e = this.edition();
    if (!e) { return false; }
    if (e.phase === 'PREPARATION') { return false; }
    if (e.phase === 'CLOSED' && e.archived) { return false; }
    return true;
  }

  nextPhase(): PhaseType | null {
    const current = this.edition()?.phase;
    if (!current) { return null; }
    const idx = PHASE_ORDER.indexOf(current);
    return idx < PHASE_ORDER.length - 1 ? PHASE_ORDER[idx + 1] : null;
  }

  prevPhase(): PhaseType | null {
    const current = this.edition()?.phase;
    if (!current) { return null; }
    const idx = PHASE_ORDER.indexOf(current);
    return idx > 0 ? PHASE_ORDER[idx - 1] : null;
  }

  confirmAdvance(): void {
    const e = this.edition();
    if (!e || !this.canAdvance() || this.isSubmitting()) { return; }
    const next = this.nextPhase()!;
    const nextLabel = this.translate.instant('edition.phase.' + next);
    this.isSubmitting.set(true);
    try {
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
        } catch {
          this.toast.showError(this.translate.instant('phase.advance.error'));
        } finally {
          this.isSubmitting.set(false);
        }
      });
    } catch {
      this.isSubmitting.set(false);
    }
  }

  confirmRollback(): void {
    const e = this.edition();
    if (!e || !this.canRollback() || this.isSubmitting()) { return; }
    const prev = this.prevPhase()!;
    const prevLabel = this.translate.instant('edition.phase.' + prev);
    this.isSubmitting.set(true);
    try {
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
        } catch {
          this.toast.showError(this.translate.instant('phase.rollback.error'));
        } finally {
          this.isSubmitting.set(false);
        }
      });
    } catch {
      this.isSubmitting.set(false);
    }
  }
}
