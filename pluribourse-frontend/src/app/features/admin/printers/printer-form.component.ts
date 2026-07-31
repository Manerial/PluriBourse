import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { DiscoveredPrinter } from '../../../models/printer-registry.model';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { DialogShellComponent } from '../../../shared/components/dialog-shell/dialog-shell.component';
import { ToastService } from '../../../shared/components/toast/toast.service';

const THERMAL_WIDTH_57 = 57;
const THERMAL_WIDTH_80 = 80;

export interface PrinterFormDialogData {
  discoveredPrinters: DiscoveredPrinter[];
  discoveryError: boolean;
}

@Component({
  selector: 'app-printer-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslatePipe, NotificationInlineComponent, DialogShellComponent],
  templateUrl: './printer-form.component.html'
})
export class PrinterFormComponent {
  private readonly printerRegistryService = inject(PrinterRegistryService);
  private readonly fb = inject(FormBuilder);
  private readonly dialogData = inject<PrinterFormDialogData>(DIALOG_DATA);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  readonly dialogRef = inject<DialogRef<void>>(DialogRef);

  readonly widthOptions = [THERMAL_WIDTH_57, THERMAL_WIDTH_80];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    printerBridgeId: ['', [Validators.required]],
    widthMm: [null as number | null],
  });

  readonly discoveredPrinters = signal<DiscoveredPrinter[]>(this.dialogData.discoveredPrinters);
  readonly discoveryError = signal(this.dialogData.discoveryError);
  readonly selectedPrinter = signal<DiscoveredPrinter | null>(null);
  readonly selectedType = signal<'THERMAL' | 'A4' | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly ignoringId = signal<string | null>(null);

  constructor() {
    this.form.controls.printerBridgeId.valueChanges.subscribe(id => this.applySelectedPrinter(id));
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || !this.selectedType()) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    try {
      const { name, printerBridgeId, widthMm } = this.form.getRawValue();
      await firstValueFrom(this.printerRegistryService.create({
        name,
        type: this.selectedType()!,
        printerBridgeId,
        widthMm: this.selectedType() === 'THERMAL' ? widthMm : null,
      }));
      this.dialogRef.close();
    } catch {
      this.error.set('admin.printers.error.create');
    } finally {
      this.loading.set(false);
    }
  }

  cancel(): void {
    this.dialogRef.close();
  }

  selectRow(printer: DiscoveredPrinter): void {
    this.form.controls.printerBridgeId.setValue(printer.printerBridgeId);
    this.selectedPrinter.set(printer);
  }

  backToList(): void {
    this.selectedPrinter.set(null);
    this.form.reset();
    this.error.set(null);
  }

  async ignoreRow(printer: DiscoveredPrinter): Promise<void> {
    this.ignoringId.set(printer.printerBridgeId);
    try {
      await firstValueFrom(this.printerRegistryService.ignore(printer.printerBridgeId, printer.name));
      this.discoveredPrinters.update(list => list.filter(p => p.printerBridgeId !== printer.printerBridgeId));
      this.toast.showSuccess(this.translate.instant('admin.printers.success.ignore'));
    } catch {
      this.toast.showError(this.translate.instant('admin.printers.error.ignore'));
    } finally {
      this.ignoringId.set(null);
    }
  }

  private applySelectedPrinter(printerBridgeId: string): void {
    const printer = this.discoveredPrinters().find(p => p.printerBridgeId === printerBridgeId);
    this.selectedType.set(printer?.type ?? null);
    const widthMm = this.form.controls.widthMm;
    if (this.selectedType() === 'THERMAL') {
      widthMm.setValidators([Validators.required]);
    } else {
      widthMm.clearValidators();
      widthMm.setValue(null);
    }
    widthMm.updateValueAndValidity();
  }
}
