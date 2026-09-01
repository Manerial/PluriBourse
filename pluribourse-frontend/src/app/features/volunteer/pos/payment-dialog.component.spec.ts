import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { MatRadioChange } from '@angular/material/radio';
import { vi } from 'vitest';
import { PaymentDialogComponent, PaymentDialogData } from './payment-dialog.component';
import { ScanResult } from '../../../models/pos.model';

const MOCK_ITEM: ScanResult = { itemId: 1, name: 'Kapla', price: 5, incomplete: false, comment: null, lotId: null };

const testData: PaymentDialogData = {
  items: [MOCK_ITEM],
  total: 5,
  currency: '€',
};

describe('PaymentDialogComponent', () => {
  const mockClose = vi.fn();
  const mockDialogRef = { close: mockClose };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [PaymentDialogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: mockDialogRef },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('disables the confirm button until a payment method is selected', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    const confirmBtn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-flat-button]');
    expect(confirmBtn.disabled).toBe(true);
  });

  it('shows the amount given field only when CASH is selected', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash-detail')).toBeNull();

    fixture.componentInstance.selectMethod({ value: 'CASH' } as MatRadioChange);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash-detail')).not.toBeNull();

    fixture.componentInstance.selectMethod({ value: 'CHECK' } as MatRadioChange);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.cash-detail')).toBeNull();
  });

  it('computes and displays the change due once an amount given is entered (AC6)', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectMethod({ value: 'CASH' } as MatRadioChange);
    fixture.componentInstance.amountGiven.set(10);
    fixture.detectChanges();
    expect(fixture.componentInstance.changeDue()).toBe(5);
    expect(fixture.nativeElement.querySelector('.cash-detail__change')).not.toBeNull();
  });

  it('never shows a change due for CHECK or CARD', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectMethod({ value: 'CARD' } as MatRadioChange);
    fixture.detectChanges();
    expect(fixture.componentInstance.changeDue()).toBeNull();
  });

  it('disables confirm when the amount given is below the total, including negative amounts', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectMethod({ value: 'CASH' } as MatRadioChange);
    fixture.componentInstance.amountGiven.set(-1);
    fixture.detectChanges();
    expect(fixture.componentInstance.confirmDisabled()).toBe(true);

    fixture.componentInstance.amountGiven.set(4.99);
    fixture.detectChanges();
    expect(fixture.componentInstance.confirmDisabled()).toBe(true);

    fixture.componentInstance.amountGiven.set(5);
    fixture.detectChanges();
    expect(fixture.componentInstance.confirmDisabled()).toBe(false);
  });

  it('confirm() closes with amountGiven: null when the method is not CASH, even if an amount was entered earlier (regression)', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectMethod({ value: 'CASH' } as MatRadioChange);
    fixture.componentInstance.amountGiven.set(10);
    fixture.componentInstance.selectMethod({ value: 'CARD' } as MatRadioChange);
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith({ request: { paymentMethod: 'CARD', amountGiven: null }, printInvoice: true });
  });

  it('confirm() closes with the CASH payload including the amount given', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectMethod({ value: 'CASH' } as MatRadioChange);
    fixture.componentInstance.amountGiven.set(10);
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith({ request: { paymentMethod: 'CASH', amountGiven: 10 }, printInvoice: true });
  });

  it('the "print invoice" checkbox is checked by default (story 4.7 AC1)', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.printInvoice()).toBe(true);
    const checkbox: HTMLInputElement = fixture.nativeElement.querySelector('.print-invoice-option input[type="checkbox"]');
    expect(checkbox.checked).toBe(true);
  });

  it('confirm() carries printInvoice: false once the box is unchecked (story 4.7 AC1, AC2)', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectMethod({ value: 'CARD' } as MatRadioChange);
    fixture.componentInstance.printInvoice.set(false);
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith({ request: { paymentMethod: 'CARD', amountGiven: null }, printInvoice: false });
  });

  it('confirm() does nothing while disabled', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.confirm();
    expect(mockClose).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog with undefined', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(undefined);
  });

  it('close button calls cancel()', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.dialog-shell__close').click();
    expect(mockClose).toHaveBeenCalledWith(undefined);
  });

  it('Enter in the amount given field moves focus to the confirm button without confirming', () => {
    const fixture = TestBed.createComponent(PaymentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.selectMethod({ value: 'CASH' } as MatRadioChange);
    fixture.componentInstance.amountGiven.set(5);
    fixture.detectChanges();

    const preventDefault = vi.fn();
    fixture.componentInstance.onAmountGivenEnter({ preventDefault } as unknown as Event);

    expect(preventDefault).toHaveBeenCalled();
    expect(mockClose).not.toHaveBeenCalled();
    expect(document.activeElement).toBe(fixture.nativeElement.querySelector('button[mat-flat-button]'));
  });
});
