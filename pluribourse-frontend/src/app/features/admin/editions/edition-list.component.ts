import { Component, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { DestroyRef } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { EditionDto } from '../../../models/edition.model';
import { EditionService } from '../../../services/edition.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-edition-list',
  standalone: true,
  imports: [DecimalPipe, RouterLink, MatButtonModule, MatIconModule, TranslatePipe, SkeletonRowComponent, EmptyStateComponent, NotificationInlineComponent],
  templateUrl: './edition-list.component.html',
  styleUrl: './edition-list.component.scss'
})
export class EditionListComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly confirmDialog = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly editions = signal<EditionDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
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

  navigateToCreate(): void {
    this.router.navigateByUrl('/admin/editions/create');
  }

  isEditable(edition: EditionDto): boolean {
    return edition.phase === 'PREPARATION';
  }

  navigateToEdit(edition: EditionDto): void {
    this.router.navigateByUrl(`/admin/editions/${edition.id}/edit`);
  }

  confirmDelete(edition: EditionDto): void {
    this.confirmDialog.open({
      title: this.translate.instant('edition.deleteDialog.title'),
      description: this.translate.instant('edition.deleteDialog.description'),
      confirmVariant: 'error',
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (confirmed) => {
      if (!confirmed) {
        return;
      }
      try {
        await firstValueFrom(this.editionService.delete(edition.id));
        this.toast.showSuccess(this.translate.instant('edition.actions.success.delete'));
        await this.reloadEditions();
      } catch {
        this.toast.showError(this.translate.instant('edition.actions.error.delete'));
      }
    });
  }

  private async reloadEditions(): Promise<void> {
    try {
      this.editions.set(await firstValueFrom(this.editionService.getAll()));
    } catch {
      this.error.set('edition.actions.error.load');
    }
  }
}
