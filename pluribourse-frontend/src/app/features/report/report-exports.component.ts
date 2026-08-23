import { Component, inject, signal, WritableSignal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { HttpResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom, Observable } from 'rxjs';
import { ReportService } from '../../services/report.service';
import { ReportEditionScopeService } from './report-edition-scope.service';
import { ToastService } from '../../shared/components/toast/toast.service';

@Component({
  selector: 'app-report-exports',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe],
  templateUrl: './report-exports.component.html',
  styleUrl: './report-exports.component.scss',
})
export class ReportExportsComponent {
  private readonly reportService = inject(ReportService);
  private readonly scope = inject(ReportEditionScopeService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly exportingCatalog = signal(false);
  readonly exportingSettlements = signal(false);

  async exportCatalog(): Promise<void> {
    await this.runExport(this.exportingCatalog, 'catalogue.csv', (editionId) => this.reportService.exportCatalog(editionId));
  }

  async exportSettlements(): Promise<void> {
    await this.runExport(this.exportingSettlements, 'reversements.csv', (editionId) => this.reportService.exportSettlements(editionId));
  }

  private async runExport(
    inFlight: WritableSignal<boolean>,
    fileName: string,
    exportCall: (editionId: number) => Observable<HttpResponse<Blob>>
  ): Promise<void> {
    if (inFlight()) {
      return;
    }
    // selectedEditionId() can turn null between this button rendering and the click actually
    // firing (the admin switches the edition selector in that window).
    const editionId = this.scope.selectedEditionId();
    if (editionId === null) {
      return;
    }
    inFlight.set(true);
    try {
      const response = await firstValueFrom(exportCall(editionId));
      this.downloadBlob(response.body!, fileName);
      this.toast.showSuccess(this.translate.instant('admin.reports.export.success'));
    } catch {
      // responseType: 'blob' means a server error also arrives as a Blob in error.error, not
      // parsed JSON — extractErrorType() would not work here. A single generic error toast is
      // enough for this button, consistent with the rest of this component.
      this.toast.showError(this.translate.instant('admin.reports.export.error'));
    } finally {
      inFlight.set(false);
    }
  }

  private downloadBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
