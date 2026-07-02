import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { DestroyRef } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { EditionDto } from '../../../models/edition.model';
import { EditionService } from '../../../services/edition.service';
import { CurrentEditionService } from '../../../services/current-edition.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionFormComponent, EditionFormDialogData } from './edition-form.component';
import { PhaseControlComponent, PhaseControlDialogData } from './phase-control/phase-control.component';
import { EditionCategoriesComponent, EditionCategoriesDialogData } from './edition-categories/edition-categories.component';

@Component({
  selector: 'app-edition-list',
  standalone: true,
  imports: [DecimalPipe, MatButtonModule, MatIconModule, MatTooltipModule, TranslatePipe, SkeletonRowComponent, EmptyStateComponent, NotificationInlineComponent],
  templateUrl: './edition-list.component.html',
  styleUrl: './edition-list.component.scss'
})
export class EditionListComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly dialog = inject(Dialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly editions = signal<EditionDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  private isConfirmingDelete = false;

  async ngOnInit(): Promise<void> {
    await this.loadEditions();
  }

  private async loadEditions(): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.editions.set(await firstValueFrom(this.editionService.getAll()));
    } catch {
      this.error.set('edition.actions.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  private openEditionDialog(editionId: number | null, ariaLabel: string): void {
    const ref = this.dialog.open<void, EditionFormDialogData, EditionFormComponent>(
      EditionFormComponent,
      {
        data: { editionId },
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabel,
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadEditions();
      this.currentEditionService.loadEdition().subscribe({ error: () => {} });
    });
  }

  openCreateDialog(): void {
    this.openEditionDialog(null, this.translate.instant('edition.create.title'));
  }

  openEditDialog(edition: EditionDto): void {
    this.openEditionDialog(edition.id, edition.name);
  }

  isEditable(edition: EditionDto): boolean {
    return edition.phase === 'PREPARATION';
  }

  openPhaseDialog(edition: EditionDto): void {
    const ref = this.dialog.open<void, PhaseControlDialogData, PhaseControlComponent>(
      PhaseControlComponent,
      {
        data: { editionId: edition.id },
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabel: this.translate.instant('phase.control.title', { editionName: edition.name }),
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadEditions());
  }

  openCategoriesDialog(edition: EditionDto): void {
    const ref = this.dialog.open<void, EditionCategoriesDialogData, EditionCategoriesComponent>(
      EditionCategoriesComponent,
      {
        data: { editionId: edition.id },
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabel: this.translate.instant('category.title', { editionName: edition.name }),
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadEditions());
  }

  confirmDelete(edition: EditionDto): void {
    if (this.isConfirmingDelete) {
      return;
    }
    this.isConfirmingDelete = true;
    this.confirmDialog.open({
      title: this.translate.instant('edition.deleteDialog.title'),
      description: this.translate.instant('edition.deleteDialog.description'),
      confirmVariant: 'error',
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) {
        this.isConfirmingDelete = false;
        return;
      }
      try {
        await firstValueFrom(this.editionService.delete(edition.id));
        this.toast.showSuccess(this.translate.instant('edition.actions.success.delete'));
        // Remove locally instead of re-fetching the list: the delete already succeeded,
        // so a subsequent GET failure must not hide the (still valid) remaining rows.
        this.editions.update(list => list.filter(e => e.id !== edition.id));
        this.currentEditionService.loadEdition().subscribe({ error: () => {} });
      } catch {
        this.toast.showError(this.translate.instant('edition.actions.error.delete'));
      } finally {
        this.isConfirmingDelete = false;
      }
    });
  }
}
