import { Component, computed, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { DailySalesReportDto } from '../../models/daily-sales-report.model';
import { ActivePhase } from '../../models/active-phase.enum';
import { ReportService } from '../../services/report.service';
import { CurrentEditionService } from '../../services/current-edition.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../shared/components/skeleton-row/skeleton-row.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../shared/http-error.util';

@Component({
  selector: 'app-report-page',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe, SkeletonRowComponent, EmptyStateComponent, NotificationInlineComponent],
  templateUrl: './report-page.component.html',
  styleUrl: './report-page.component.scss',
})
export class ReportPageComponent {
  private readonly reportService = inject(ReportService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly report = signal<DailySalesReportDto | null>(null);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly printing = signal(false);

  readonly isSalePhase = computed(() => this.currentEditionService.currentEdition()?.phase === ActivePhase.SALE);

  constructor() {
    // effect() réactif, pas un ngOnInit ponctuel : AppLayoutComponent.loadEdition() (parent) résout
    // de façon asynchrone après le montage de ce composant enfant — currentEdition() vaut donc
    // souvent encore null au moment où ngOnInit s'exécuterait. Réagit dans les deux sens : charge
    // dès que la phase Vente devient vraie (y compris après résolution tardive), et remet à null si
    // la phase change pendant la consultation (cohérent avec le "absent, pas grisée" de la Story 5.5).
    effect(() => {
      if (this.isSalePhase()) {
        void this.load();
      } else {
        this.report.set(null);
      }
    });
  }

  async refresh(): Promise<void> {
    await this.load();
  }

  private async load(): Promise<void> {
    if (this.isLoading()) {
      return;
    }
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.report.set(await firstValueFrom(this.reportService.getDailyReport()));
    } catch {
      this.error.set('admin.reports.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  async printReport(): Promise<void> {
    if (this.printing()) {
      return;
    }
    this.printing.set(true);
    try {
      await firstValueFrom(this.reportService.printDailyReport());
      this.toast.showSuccess(this.translate.instant('admin.reports.success.print'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('admin.reports.error.printerUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('admin.reports.error.print'));
      }
    } finally {
      this.printing.set(false);
    }
  }
}
