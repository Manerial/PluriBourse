import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatExpansionModule } from '@angular/material/expansion';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { Dialog } from '@angular/cdk/dialog';
import { DiscoveredPrinter, IgnoredPrinter, PrinterSummary, PrintResult } from '../../../models/printer-registry.model';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { PrinterFormComponent, PrinterFormDialogData } from './printer-form.component';

@Component({
  selector: 'app-printer-list',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatExpansionModule, TranslatePipe, SkeletonRowComponent, NotificationInlineComponent, EmptyStateComponent],
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
  readonly testingId = signal<number | null>(null);
  readonly discovering = signal(false);
  readonly ignoredPrinters = signal<IgnoredPrinter[]>([]);
  readonly reactivatingId = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.load();
  }

  async testPrint(printer: PrinterSummary): Promise<void> {
    this.testingId.set(printer.id);
    try {
      const result: PrintResult = await firstValueFrom(this.printerRegistryService.testPrint(printer.id));
      if (result.status === 'OK') {
        this.toast.showSuccess(this.translate.instant('admin.printers.success.testPrint'));
      } else {
        this.toast.showError(result.message ?? this.translate.instant('admin.printers.error.testPrint'));
      }
    } catch {
      this.toast.showError(this.translate.instant('admin.printers.error.testPrint'));
    } finally {
      this.testingId.set(null);
    }
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

  async openCreateDialog(): Promise<void> {
    this.discovering.set(true);
    let discoveredPrinters: DiscoveredPrinter[] = [];
    let discoveryError = false;
    try {
      discoveredPrinters = await firstValueFrom(this.printerRegistryService.discover());
    } catch {
      discoveryError = true;
    } finally {
      this.discovering.set(false);
    }

    const ref = this.dialog.open<void, PrinterFormDialogData, PrinterFormComponent>(
      PrinterFormComponent,
      {
        data: { discoveredPrinters, discoveryError },
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabel: this.translate.instant('admin.printers.create.title'),
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  async reactivate(printer: IgnoredPrinter): Promise<void> {
    this.reactivatingId.set(printer.printerBridgeId);
    try {
      await firstValueFrom(this.printerRegistryService.reactivate(printer.printerBridgeId));
      this.ignoredPrinters.update(list => list.filter(p => p.printerBridgeId !== printer.printerBridgeId));
      this.toast.showSuccess(this.translate.instant('admin.printers.success.reactivate'));
    } catch {
      this.toast.showError(this.translate.instant('admin.printers.error.reactivate'));
    } finally {
      this.reactivatingId.set(null);
    }
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

    try {
      this.ignoredPrinters.set(await firstValueFrom(this.printerRegistryService.listIgnored()));
    } catch {
      this.ignoredPrinters.set([]);
    }
  }
}
