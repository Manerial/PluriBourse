import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { Dialog } from '@angular/cdk/dialog';
import { PrinterSummary } from '../../../models/printer-registry.model';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { PrinterFormComponent } from './printer-form.component';

@Component({
  selector: 'app-printer-list',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe, SkeletonRowComponent, NotificationInlineComponent, EmptyStateComponent],
  templateUrl: './printer-list.component.html',
  styleUrl: './printer-list.component.scss'
})
export class PrinterListComponent implements OnInit {
  private readonly printerRegistryService = inject(PrinterRegistryService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly dialog = inject(Dialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly confirmDialog = inject(ConfirmDialogService);

  readonly printers = signal<PrinterSummary[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  async confirmDelete(printer: PrinterSummary): Promise<void> {
    const confirmed = await firstValueFrom(
      this.confirmDialog.open({
        title: this.translate.instant('admin.printers.deleteDialog.title'),
        description: this.translate.instant('admin.printers.deleteDialog.description'),
        confirmVariant: 'error',
      })
    );
    if (!confirmed) {
      return;
    }
    this.submitting.set(true);
    try {
      await firstValueFrom(this.printerRegistryService.delete(printer.id));
      this.toast.showSuccess(this.translate.instant('admin.printers.success.delete'));
      this.printers.update(list => list.filter(p => p.id !== printer.id));
    } catch {
      this.toast.showError(this.translate.instant('admin.printers.error.delete'));
    } finally {
      this.submitting.set(false);
    }
  }

  openCreateDialog(): void {
    const ref = this.dialog.open<void, void, PrinterFormComponent>(
      PrinterFormComponent,
      {
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabel: this.translate.instant('admin.printers.create.title'),
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  private async load(): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.printers.set(await firstValueFrom(this.printerRegistryService.list()));
    } catch {
      this.error.set('admin.printers.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }
}
