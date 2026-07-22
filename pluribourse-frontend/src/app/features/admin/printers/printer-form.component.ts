import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { SerialPortOption } from '../../../models/printer-registry.model';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { DialogShellComponent } from '../../../shared/components/dialog-shell/dialog-shell.component';

const THERMAL_WIDTH_57 = 57;
const THERMAL_WIDTH_80 = 80;
const TCP_PORT_MIN = 1;
const TCP_PORT_MAX = 65535;

@Component({
  selector: 'app-printer-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslatePipe, NotificationInlineComponent, DialogShellComponent],
  templateUrl: './printer-form.component.html'
})
export class PrinterFormComponent implements OnInit {
  private readonly printerRegistryService = inject(PrinterRegistryService);
  private readonly fb = inject(FormBuilder);
  readonly dialogRef = inject<DialogRef<void>>(DialogRef);

  readonly widthOptions = [THERMAL_WIDTH_57, THERMAL_WIDTH_80];

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    type: ['THERMAL' as 'THERMAL' | 'A4', [Validators.required]],
    serialPort: [null as string | null],
    widthMm: [null as number | null],
    host: [null as string | null],
    port: [null as number | null, [Validators.min(TCP_PORT_MIN), Validators.max(TCP_PORT_MAX)]],
  });

  readonly serialPorts = signal<SerialPortOption[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.form.controls.type.valueChanges.subscribe(type => this.applyValidatorsForType(type));
    this.applyValidatorsForType(this.form.controls.type.value);
    this.loadSerialPorts();
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    try {
      const { name, type, serialPort, widthMm, host, port } = this.form.getRawValue();
      await firstValueFrom(this.printerRegistryService.create({ name, type, serialPort, widthMm, host, port }));
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

  private async loadSerialPorts(): Promise<void> {
    try {
      this.serialPorts.set(await firstValueFrom(this.printerRegistryService.listSerialPorts()));
    } catch {
      this.serialPorts.set([]);
    }
  }

  private applyValidatorsForType(type: 'THERMAL' | 'A4'): void {
    const { serialPort, widthMm, host, port } = this.form.controls;
    if (type === 'THERMAL') {
      serialPort.setValidators([Validators.required]);
      widthMm.setValidators([Validators.required]);
      host.clearValidators();
      // Clears the A4-only fields left over from a previous switch so they can never be submitted
      // alongside a THERMAL payload — PrinterMapper.toEntity() maps every DTO field as-is.
      host.setValue(null);
      port.setValue(null);
    } else {
      serialPort.clearValidators();
      widthMm.clearValidators();
      host.setValidators([Validators.required, Validators.maxLength(255)]);
      serialPort.setValue(null);
      widthMm.setValue(null);
    }
    serialPort.updateValueAndValidity();
    widthMm.updateValueAndValidity();
    host.updateValueAndValidity();
  }
}
