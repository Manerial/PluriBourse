import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule, MatSelectChange } from '@angular/material/select';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { DailySalesReportDto } from '../../models/daily-sales-report.model';
import { EditionDto } from '../../models/edition.model';
import { ActivePhase } from '../../models/active-phase.enum';
import { ReportService } from '../../services/report.service';
import { EditionService } from '../../services/edition.service';
import { CurrentEditionService } from '../../services/current-edition.service';
import { ReportEditionScopeService } from './report-edition-scope.service';
import { ToastService } from '../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../shared/components/skeleton-row/skeleton-row.component';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../shared/http-error.util';

// An edition is "reportable" once its sale phase has ended: the financial summary and CSV
// exports only make sense from Post-vente onward (see AdminReportController.getEditionReport,
// resolved by explicit edition ID so it also serves CLOSED/archived editions).
const REPORTABLE_PHASES = new Set<string>([ActivePhase.POST_SALE, 'CLOSED']);

@Component({
  selector: 'app-report-page',
  standalone: true,
  imports: [
    RouterLink, RouterLinkActive, RouterOutlet, MatTabsModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatSelectModule,
    TranslatePipe, SkeletonRowComponent, EmptyStateComponent, NotificationInlineComponent,
  ],
  templateUrl: './report-page.component.html',
  styleUrl: './report-page.component.scss',
})
export class ReportPageComponent {
  private readonly reportService = inject(ReportService);
  private readonly editionService = inject(EditionService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly scope = inject(ReportEditionScopeService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly report = signal<DailySalesReportDto | null>(null);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly printing = signal(false);

  readonly editions = signal<EditionDto[]>([]);
  readonly isLoadingEditions = signal(false);
  readonly selectedEditionId = this.scope.selectedEditionId;

  readonly reportableEditions = computed(() =>
    this.editions()
      .filter((edition) => REPORTABLE_PHASES.has(edition.phase))
      .sort((a, b) => b.startDate.localeCompare(a.startDate) || b.id - a.id)
  );

  readonly isSalePhase = computed(() => this.currentEditionService.currentEdition()?.phase === ActivePhase.SALE);

  constructor() {
    // effect() réactif, pas un ngOnInit ponctuel : AppLayoutComponent.loadEdition() (parent) résout
    // de façon asynchrone après le montage de ce composant enfant — currentEdition() vaut donc
    // souvent encore null au moment où ngOnInit s'exécuterait. Réagit dans les deux sens : charge
    // dès que la phase Vente devient vraie (y compris après résolution tardive), et remet à null si
    // la phase change pendant la consultation (cohérent avec le "absent, pas grisée" de la Story 5.5).
    effect(() => {
      const salePhase = this.isSalePhase();
      // untracked() is required here, not just style: load() reads its own isLoading signal
      // synchronously before its first await, which — left untracked — Angular would register as
      // a dependency of this effect. Its `finally` block then flips that same signal back once the
      // request settles, outside this synchronous run, which would re-trigger this effect and call
      // load() again: a self-sustaining fetch loop paced by the network round-trip, not a one-off
      // load (see the identical bug this fixed for the edition report, now EditionReportComponent).
      untracked(() => {
        if (salePhase) {
          void this.load();
        } else {
          this.report.set(null);
        }
      });
    });

    // Re-fetches the edition list whenever currentEdition() changes (mount, and every later SSE
    // phase-changed event) so an edition entering Post-vente shows up in the selector without a
    // manual page reload.
    effect(() => {
      this.currentEditionService.currentEdition();
      untracked(() => {
        void this.loadEditions();
      });
    });
  }

  onEditionChange(event: MatSelectChange): void {
    this.scope.selectedEditionId.set(event.value as number);
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

  private async loadEditions(): Promise<void> {
    this.isLoadingEditions.set(true);
    try {
      this.editions.set(await firstValueFrom(this.editionService.getAll()));
    } catch {
      this.editions.set([]);
    } finally {
      this.isLoadingEditions.set(false);
    }
    this.applyDefaultSelection();
  }

  // Picks a default selection the first time reportable editions become available, preferring
  // the current edition if it qualifies. Only runs while nothing is selected yet, so it never
  // fights an admin's manual choice from the selector. Called synchronously right after
  // editions() is populated, rather than from a separate effect() reacting to reportableEditions()
  // — an effect chained onto another effect's async result needs an extra change-detection round
  // to flush, which is fragile both in tests and in production timing.
  private applyDefaultSelection(): void {
    if (this.scope.selectedEditionId() !== null) {
      return;
    }
    const options = this.reportableEditions();
    if (options.length === 0) {
      return;
    }
    const current = this.currentEditionService.currentEdition();
    const defaultEdition = options.find((edition) => edition.id === current?.id) ?? options[0];
    this.scope.selectedEditionId.set(defaultEdition.id);
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
