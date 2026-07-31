import { Component, ElementRef, Signal, computed, inject, signal, viewChild } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatRadioChange, MatRadioModule } from '@angular/material/radio';
import { TranslatePipe } from '@ngx-translate/core';
import Big from 'big.js';
import { PaymentMethod, ScanResult, ValidateBasketRequest } from '../../../models/pos.model';
import { DialogShellComponent } from '../../../shared/components/dialog-shell/dialog-shell.component';

export interface PaymentDialogData {
  items: ScanResult[];
  total: number;
}

@Component({
  selector: 'app-payment-dialog',
  standalone: true,
  imports: [A11yModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatRadioModule, TranslatePipe, DialogShellComponent],
  templateUrl: './payment-dialog.component.html',
  styleUrl: './payment-dialog.component.scss',
})
export class PaymentDialogComponent {
  readonly dialogRef = inject<DialogRef<ValidateBasketRequest | undefined>>(DialogRef);
  readonly data = inject<PaymentDialogData>(DIALOG_DATA);

  // Explicit `read: ElementRef` — MatButton is itself a component (MDC-based), so a bare
  // #confirmButton template-variable query would otherwise resolve to the MatButton instance
  // instead of the native <button> element.
  readonly confirmButton: Signal<ElementRef<HTMLButtonElement> | undefined> = viewChild('confirmButton', { read: ElementRef });

  readonly paymentMethod = signal<PaymentMethod | null>(null);
  readonly amountGiven = signal<number | null>(null);

  readonly changeDue = computed(() => {
    const given = this.amountGiven();
    if (this.paymentMethod() !== 'CASH' || given === null) {
      return null;
    }
    return new Big(given).minus(this.data.total).toNumber();
  });

  // A given amount below the total (including negative) must never be confirmable — no AC
  // requires it explicitly, but silently accepting it would be a business-logic regression.
  readonly confirmDisabled = computed(() => {
    const method = this.paymentMethod();
    if (method === null) {
      return true;
    }
    const given = this.amountGiven();
    return method === 'CASH' && given !== null && given < this.data.total;
  });

  selectMethod(event: MatRadioChange): void {
    const method = event.value as PaymentMethod;
    this.paymentMethod.set(method);
    if (method !== 'CASH') {
      this.amountGiven.set(null);
    }
  }

  onAmountGivenChange(event: Event): void {
    const value = (event.target as HTMLInputElement).valueAsNumber;
    this.amountGiven.set(Number.isNaN(value) ? null : value);
  }

  onAmountGivenEnter(event: Event): void {
    event.preventDefault();
    this.confirmButton()?.nativeElement.focus();
  }

  confirm(): void {
    if (this.confirmDisabled()) {
      return;
    }
    this.dialogRef.close({
      paymentMethod: this.paymentMethod()!,
      amountGiven: this.paymentMethod() === 'CASH' ? this.amountGiven() : null,
    });
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
