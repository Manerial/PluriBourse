import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { AvailablePrinter } from '../../../models/printer.model';
import { PrintService } from '../../../services/print.service';
import { AuthService } from '../../../services/auth.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';

@Component({
  selector: 'app-printer-selection',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatSelectModule, TranslatePipe, NotificationInlineComponent],
  templateUrl: './printer-selection.component.html',
  styleUrl: './printer-selection.component.scss',
})
export class PrinterSelectionComponent implements OnInit {
  private readonly printService = inject(PrintService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly thermalPrinters = signal<AvailablePrinter[]>([]);
  readonly a4Printers = signal<AvailablePrinter[]>([]);
  readonly loading = signal(false);
  readonly error = signal(false);

  readonly form = this.fb.nonNullable.group({
    thermalPrinterId: null as number | null,
    a4PrinterId: null as number | null,
  });

  async ngOnInit(): Promise<void> {
    try {
      const printers = await firstValueFrom(this.printService.getAvailablePrinters());
      this.thermalPrinters.set(printers.filter(printer => printer.type === 'THERMAL'));
      this.a4Printers.set(printers.filter(printer => printer.type === 'A4'));
    } catch {
      this.error.set(true);
    }
  }

  async onSubmit(): Promise<void> {
    this.error.set(false);
    this.loading.set(true);
    const { thermalPrinterId, a4PrinterId } = this.form.getRawValue();
    try {
      await firstValueFrom(this.printService.submitSelection(thermalPrinterId, a4PrinterId));
      this.auth.markPrinterSelectionDone();
      await this.router.navigate(['/volunteer']);
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
