import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PosPageComponent } from './pos-page.component';
import { PosService } from '../../../services/pos.service';
import { PaymentDialogService } from './payment-dialog.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { Basket, ScanResult } from '../../../models/pos.model';

const ITEM_1: ScanResult = { itemId: 1, name: 'Kapla', price: 5, incomplete: false, comment: null, lotId: null };
const ITEM_2_INCOMPLETE: ScanResult = {
  itemId: 2,
  name: 'Puzzle',
  price: 3,
  incomplete: true,
  comment: 'Manque une piece',
  lotId: null,
};
const LOT_ITEM_A: ScanResult = { itemId: 3, name: 'Lot item A', price: null, incomplete: false, comment: null, lotId: 100 };
const LOT_ITEM_B: ScanResult = { itemId: 4, name: 'Lot item B', price: null, incomplete: false, comment: null, lotId: 100 };

const EMPTY_BASKET: Basket = { id: 10, items: [], lotGroups: [], total: 0 };
const BASKET_WITH_ITEM_1: Basket = { id: 10, items: [ITEM_1], lotGroups: [], total: 5 };
const BASKET_WITH_INCOMPLETE_LOT: Basket = {
  id: 10,
  items: [ITEM_1, LOT_ITEM_A],
  lotGroups: [{ lotId: 100, lotName: 'Lot Jouets', globalPrice: 10, scannedCount: 1, totalCount: 2 }],
  total: 15,
};
const BASKET_WITH_COMPLETE_LOT: Basket = {
  id: 10,
  items: [LOT_ITEM_A, LOT_ITEM_B],
  lotGroups: [{ lotId: 100, lotName: 'Lot Jouets', globalPrice: 10, scannedCount: 2, totalCount: 2 }],
  total: 10,
};

describe('PosPageComponent', () => {
  let fixture: ComponentFixture<PosPageComponent>;
  let component: PosPageComponent;

  const posServiceMock = {
    getCurrentBasket: vi.fn(),
    addItem: vi.fn(),
    removeItem: vi.fn(),
    removeLot: vi.fn(),
    validate: vi.fn(),
    printInvoice: vi.fn(),
  };
  const paymentDialogServiceMock = { open: vi.fn() };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  async function createComponent(initialBasket: Basket = EMPTY_BASKET): Promise<void> {
    posServiceMock.getCurrentBasket.mockReturnValue(of(initialBasket));

    await TestBed.configureTestingModule({
      imports: [PosPageComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: PosService, useValue: posServiceMock },
        { provide: PaymentDialogService, useValue: paymentDialogServiceMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PosPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads the persisted basket on init and restores it (NFR-006)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    expect(posServiceMock.getCurrentBasket).toHaveBeenCalled();
    expect(component.basket()).toEqual(BASKET_WITH_ITEM_1);
  });

  it('adds the item to the basket on a successful scan and clears any prior issue (AC1, AC2)', async () => {
    await createComponent();
    posServiceMock.addItem.mockReturnValue(of(BASKET_WITH_ITEM_1));
    await component.onScan('00010001');
    expect(posServiceMock.addItem).toHaveBeenCalledWith(EMPTY_BASKET.id, '00010001');
    expect(component.basket()).toEqual(BASKET_WITH_ITEM_1);
    expect(component.lastScanIssue()).toBeNull();
  });

  it('shows a warning when the newly added item is incomplete', async () => {
    await createComponent();
    posServiceMock.addItem.mockReturnValue(of({ id: 10, items: [ITEM_2_INCOMPLETE], lotGroups: [], total: 3 }));
    await component.onScan('00010002');
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.warning.incomplete', variant: 'warning' });
  });

  it('shows the existing warning without duplicating when the server reports item-already-in-basket (AC2)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    posServiceMock.addItem.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { type: 'https://pluribourse/errors/item-already-in-basket' } }))
    );
    await component.onScan('00010001');
    expect(component.basket()).toEqual(BASKET_WITH_ITEM_1);
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.warning.alreadyInBasket', variant: 'warning' });
  });

  it('shows an inline error when already sold (AC5, story 4.1 contract reused)', async () => {
    await createComponent();
    posServiceMock.addItem.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { type: 'https://pluribourse/errors/item-already-sold' } }))
    );
    await component.onScan('00010001');
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.error.alreadySold', variant: 'error' });
  });

  it('removing an item calls removeItem() and updates the basket display (AC3)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    posServiceMock.removeItem.mockReturnValue(of(EMPTY_BASKET));
    await component.removeItem(ITEM_1.itemId);
    expect(posServiceMock.removeItem).toHaveBeenCalledWith(BASKET_WITH_ITEM_1.id, ITEM_1.itemId);
    expect(component.basket()).toEqual(EMPTY_BASKET);
  });

  it('disables the validate button when the basket is empty (AC4)', async () => {
    await createComponent(EMPTY_BASKET);
    const validateBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.basket-validate');
    expect(validateBtn.disabled).toBe(true);
  });

  it('enables the validate button once the basket has items (AC4)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    const validateBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.basket-validate');
    expect(validateBtn.disabled).toBe(false);
  });

  it('an incomplete lot is rendered as a separate group with the scanned counter and a warning notification (AC1, AC2)', async () => {
    await createComponent(BASKET_WITH_INCOMPLETE_LOT);
    const lotGroup: HTMLElement = fixture.nativeElement.querySelector('.lot-group');
    expect(lotGroup).not.toBeNull();
    expect(lotGroup.classList.contains('lot-group--complete')).toBe(false);
    const warning: HTMLElement = fixture.nativeElement.querySelector('app-notification-inline');
    expect(warning).not.toBeNull();
  });

  it('a complete lot shows no warning notification (AC3)', async () => {
    await createComponent(BASKET_WITH_COMPLETE_LOT);
    const lotGroup: HTMLElement = fixture.nativeElement.querySelector('.lot-group');
    expect(lotGroup.classList.contains('lot-group--complete')).toBe(true);
    expect(fixture.nativeElement.querySelector('app-notification-inline')).toBeNull();
  });

  it('clicking "remove entire lot" calls removeLot() and updates the basket display (AC4)', async () => {
    await createComponent(BASKET_WITH_INCOMPLETE_LOT);
    posServiceMock.removeLot.mockReturnValue(of(EMPTY_BASKET));
    await component.removeLot(100);
    expect(posServiceMock.removeLot).toHaveBeenCalledWith(BASKET_WITH_INCOMPLETE_LOT.id, 100);
    expect(component.basket()).toEqual(EMPTY_BASKET);
  });

  it('the validate button stays active with an incomplete lot in the basket (AC2, non-regression)', async () => {
    await createComponent(BASKET_WITH_INCOMPLETE_LOT);
    const validateBtn: HTMLButtonElement = fixture.nativeElement.querySelector('.basket-validate');
    expect(validateBtn.disabled).toBe(false);
  });

  it('never renders an individual price for a lot item, in or out of its group (AC1)', async () => {
    await createComponent(BASKET_WITH_INCOMPLETE_LOT);
    const priceEls: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.basket-item__price'));
    expect(priceEls).toHaveLength(1);
    expect(priceEls[0].textContent).not.toContain('null');
  });

  it('validating opens the payment dialog and, on success, reloads a fresh empty basket (AC4)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(of({ id: 1, total: 5, paymentMethod: 'CASH', amountGiven: null, changeDue: null }));
    const newBasket: Basket = { id: 11, items: [], lotGroups: [], total: 0 };
    posServiceMock.getCurrentBasket.mockReturnValue(of(newBasket));

    await component.openPaymentDialog();

    expect(paymentDialogServiceMock.open).toHaveBeenCalledWith({ items: BASKET_WITH_ITEM_1.items, total: BASKET_WITH_ITEM_1.total });
    expect(posServiceMock.validate).toHaveBeenCalledWith(BASKET_WITH_ITEM_1.id, { paymentMethod: 'CASH', amountGiven: null });
    expect(component.basket()).toEqual(newBasket);
  });

  it('cancelling the payment dialog does not call validate', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of(undefined));
    await component.openPaymentDialog();
    expect(posServiceMock.validate).not.toHaveBeenCalled();
  });

  it('a conflict at validation lists the conflicting item names and does not clear the local basket (AC8)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { type: 'https://pluribourse/errors/basket-validation-conflict', conflictingItems: [{ itemId: 1, name: 'Kapla' }] },
          })
      )
    );

    await component.openPaymentDialog();

    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.error.conflict', variant: 'error' });
    expect(component.basket()).toEqual(BASKET_WITH_ITEM_1);
  });

  it('shows a generic toast on any other error', async () => {
    await createComponent();
    posServiceMock.addItem.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 422, error: { type: 'https://pluribourse/errors/sale-phase-required' } }))
    );
    await component.onScan('00010001');
    expect(component.lastScanIssue()).toBeNull();
    expect(toastMock.showError).toHaveBeenCalledWith('volunteer.pos.error.generic');
  });

  const VALIDATED_SALE = { id: 42, total: 5, paymentMethod: 'CASH' as const, amountGiven: null, changeDue: null };

  it('shows the invoice button after a successful validation, with the sale it printed (AC1, AC5)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(of(VALIDATED_SALE));
    posServiceMock.getCurrentBasket.mockReturnValue(of(EMPTY_BASKET));

    await component.openPaymentDialog();
    fixture.detectChanges();

    expect(component.lastSale()).toEqual(VALIDATED_SALE);
    expect(fixture.nativeElement.querySelector('.print-invoice-btn')).not.toBeNull();
  });

  it('hides the invoice button 30 seconds after validation, with no user action (AC5)', async () => {
    vi.useFakeTimers();
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(of(VALIDATED_SALE));
    posServiceMock.getCurrentBasket.mockReturnValue(of(EMPTY_BASKET));

    await component.openPaymentDialog();
    fixture.detectChanges();
    expect(component.lastSale()).not.toBeNull();

    vi.advanceTimersByTime(30000);
    fixture.detectChanges();

    expect(component.lastSale()).toBeNull();
    expect(fixture.nativeElement.querySelector('.print-invoice-btn')).toBeNull();
  });

  it('a scan while the invoice button is visible never clears it (AC5, non-regression)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(of(VALIDATED_SALE));
    posServiceMock.getCurrentBasket.mockReturnValue(of(EMPTY_BASKET));
    await component.openPaymentDialog();

    posServiceMock.addItem.mockReturnValue(of(EMPTY_BASKET));
    await component.onScan('00010001');

    expect(component.lastSale()).toEqual(VALIDATED_SALE);
  });

  it('printing the invoice calls the service with the sale id and shows a success toast (AC1)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(of(VALIDATED_SALE));
    posServiceMock.getCurrentBasket.mockReturnValue(of(EMPTY_BASKET));
    await component.openPaymentDialog();

    posServiceMock.printInvoice.mockReturnValue(of(undefined));
    await component.printInvoice();

    expect(posServiceMock.printInvoice).toHaveBeenCalledWith(VALIDATED_SALE.id);
    expect(toastMock.showSuccess).toHaveBeenCalledWith('volunteer.pos.invoice.success');
  });

  it('shows a dedicated toast when no A4 printer is available (AC7)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(of(VALIDATED_SALE));
    posServiceMock.getCurrentBasket.mockReturnValue(of(EMPTY_BASKET));
    await component.openPaymentDialog();

    posServiceMock.printInvoice.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 422, error: { type: 'https://pluribourse/errors/invalid-printer-selection' } }))
    );
    await component.printInvoice();

    expect(toastMock.showError).toHaveBeenCalledWith('volunteer.pos.invoice.error.a4PrinterUnavailable');
  });

  it('reprinting within the 30s window calls the service again without hiding the button (AC4)', async () => {
    await createComponent(BASKET_WITH_ITEM_1);
    paymentDialogServiceMock.open.mockReturnValue(of({ paymentMethod: 'CASH', amountGiven: null }));
    posServiceMock.validate.mockReturnValue(of(VALIDATED_SALE));
    posServiceMock.getCurrentBasket.mockReturnValue(of(EMPTY_BASKET));
    await component.openPaymentDialog();

    posServiceMock.printInvoice.mockReturnValue(of(undefined));
    await component.printInvoice();
    await component.printInvoice();

    expect(posServiceMock.printInvoice).toHaveBeenCalledTimes(2);
    expect(component.lastSale()).toEqual(VALIDATED_SALE);
  });
});
