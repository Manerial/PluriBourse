import { Component, effect, inject, signal, untracked } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { EditionSummaryReportDto } from '../../models/edition-summary-report.model';
import { ReportService } from '../../services/report.service';
import { ReportEditionScopeService } from './report-edition-scope.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../shared/http-error.util';

@Component({
  selector: 'app-edition-report',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe, SkeletonRowComponent, NotificationInlineComponent],
  templateUrl: './edition-report.component.html',
  styleUrl: './edition-report.component.scss',
})
export class EditionReportComponent {
  private readonly reportService = inject(ReportService);
  private readonly scope = inject(ReportEditionScopeService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly editionReport = signal<EditionSummaryReportDto | null>(null);
  readonly isLoadingEditionReport = signal(false);
  readonly editionReportError = signal<string | null>(null);
  readonly printingEditionReport = signal(false);

  constructor() {
    // Reactive, not a one-shot ngOnInit: ReportPageComponent picks the initial selection
    // asynchronously (it waits on the edition list), and the admin can switch editions afterward
    // via its selector — both must reload this tab's report.
    effect(() => {
      const editionId = this.scope.selectedEditionId();
      // untracked() for the same reason as ReportPageComponent.load(): loadEditionReport() reads
      // isLoadingEditionReport() before its first await, which would otherwise register as a
      // dependency and cause its own `finally` write to re-trigger this effect in a loop.
      untracked(() => {
        if (editionId !== null) {
          void this.loadEditionReport(editionId);
        } else {
          this.editionReport.set(null);
        }
      });
    });
  }

  private async loadEditionReport(editionId: number): Promise<void> {
    if (this.isLoadingEditionReport()) {
      return;
    }
    this.isLoadingEditionReport.set(true);
    this.editionReportError.set(null);
    try {
      this.editionReport.set(await firstValueFrom(this.reportService.getEditionReport(editionId)));
    } catch {
      this.editionReportError.set('admin.reports.error.loadEdition');
    } finally {
      this.isLoadingEditionReport.set(false);
    }
  }

  async printEditionReport(): Promise<void> {
    if (this.printingEditionReport()) {
      return;
    }
    // selectedEditionId() can turn null between this button rendering and the click actually
    // firing — guard instead of asserting non-null, which would throw and surface a misleading
    // "impossible d'imprimer" toast.
    const editionId = this.scope.selectedEditionId();
    if (editionId === null) {
      return;
    }
    this.printingEditionReport.set(true);
    try {
      await firstValueFrom(this.reportService.printEditionReport(editionId));
      this.toast.showSuccess(this.translate.instant('admin.reports.success.print'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('admin.reports.error.printerUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('admin.reports.error.print'));
      }
    } finally {
      this.printingEditionReport.set(false);
    }
  }
}
