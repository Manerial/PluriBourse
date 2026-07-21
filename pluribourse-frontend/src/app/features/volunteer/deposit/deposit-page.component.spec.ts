import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { DepositPageComponent } from './deposit-page.component';
import { SellerSearchComponent } from './seller-search.component';
import { CategoryService } from '../../../services/category.service';
import { DepositService } from '../../../services/deposit.service';
import { ItemService } from '../../../services/item.service';
import { LotService } from '../../../services/lot.service';
import { SellerService } from '../../../services/seller.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionCategoryDto } from '../../../models/category.model';
import { ItemDto } from '../../../models/item.model';
import { LotDto } from '../../../models/lot.model';
import { SellerDto } from '../../../models/seller.model';

const MOCK_CATEGORIES: EditionCategoryDto[] = [{ id: 1, name: 'Jouets', tableNumbers: [1, 2] }];

const MOCK_SELLER: SellerDto = { id: 5, firstName: 'Pierre', lastName: 'Martin', email: 'martin@email.com', phone: '0612345678' };

const MOCK_ITEM: ItemDto = {
  id: 10,
  sellerProfileId: 5,
  categoryId: 1,
  categoryName: 'Jouets',
  name: 'Kapla',
  price: 5,
  incomplete: false,
  comment: null,
  tableNumber: 1,
  lotId: null,
  lotName: null,
  lotPrice: null,
};

const MOCK_LOT_ITEM: ItemDto = {
  id: 11,
  sellerProfileId: 5,
  categoryId: 1,
  categoryName: 'Jouets',
  name: 'Piece de lot',
  price: null,
  incomplete: false,
  comment: null,
  tableNumber: 1,
  lotId: 20,
  lotName: 'Lot Jouets',
  lotPrice: 15,
};

const MOCK_LOT: LotDto = { id: 20, name: 'Lot Jouets', globalPrice: 15, items: [MOCK_LOT_ITEM] };

describe('DepositPageComponent', () => {
  let fixture: ComponentFixture<DepositPageComponent>;
  let component: DepositPageComponent;

  const categoryServiceMock = { getCategoriesForActiveEdition: vi.fn().mockReturnValue(of(MOCK_CATEGORIES)) };
  const itemServiceMock = {
    getBySeller: vi.fn().mockReturnValue(of([MOCK_ITEM])),
    updateCompleteness: vi.fn().mockReturnValue(of(MOCK_ITEM)),
    delete: vi.fn().mockReturnValue(of(undefined)),
  };
  const sellerServiceMock = { search: vi.fn().mockReturnValue(of([])) };
  const lotServiceMock = { create: vi.fn().mockReturnValue(of(MOCK_LOT)) };
  const depositServiceMock = { validateDeposit: vi.fn().mockReturnValue(of(undefined)) };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const confirmDialogMock = { open: vi.fn().mockReturnValue(of(true)) };

  beforeEach(async () => {
    vi.clearAllMocks();
    categoryServiceMock.getCategoriesForActiveEdition.mockReturnValue(of(MOCK_CATEGORIES));
    itemServiceMock.getBySeller.mockReturnValue(of([MOCK_ITEM]));
    itemServiceMock.updateCompleteness.mockReturnValue(of(MOCK_ITEM));
    itemServiceMock.delete.mockReturnValue(of(undefined));
    sellerServiceMock.search.mockReturnValue(of([]));
    lotServiceMock.create.mockReturnValue(of(MOCK_LOT));
    depositServiceMock.validateDeposit.mockReturnValue(of(undefined));
    confirmDialogMock.open.mockReturnValue(of(true));

    await TestBed.configureTestingModule({
      imports: [DepositPageComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: CategoryService, useValue: categoryServiceMock },
        { provide: ItemService, useValue: itemServiceMock },
        { provide: SellerService, useValue: sellerServiceMock },
        { provide: LotService, useValue: lotServiceMock },
        { provide: DepositService, useValue: depositServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ConfirmDialogService, useValue: confirmDialogMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DepositPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function getSellerSearch(): SellerSearchComponent {
    return fixture.debugElement.query(By.directive(SellerSearchComponent)).componentInstance as SellerSearchComponent;
  }

  function selectMockSeller(): void {
    getSellerSearch().selectSeller(MOCK_SELLER);
  }

  it('loads categories on init', () => {
    expect(categoryServiceMock.getCategoriesForActiveEdition).toHaveBeenCalled();
    expect(component.categories()).toEqual(MOCK_CATEGORIES);
  });

  it('does not load items when no seller is selected', () => {
    expect(component.selectedSeller()).toBeNull();
    expect(itemServiceMock.getBySeller).not.toHaveBeenCalled();
    expect(component.items()).toEqual([]);
  });

  it('loads items when a seller is selected', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.selectedSeller()).toEqual(MOCK_SELLER);
    expect(itemServiceMock.getBySeller).toHaveBeenCalledWith(5);
    expect(component.items()).toEqual([MOCK_ITEM]);
  });

  it('toggleIncomplete() calls updateCompleteness with the flipped flag and updates the list', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();

    await component.toggleIncomplete(MOCK_ITEM);

    expect(itemServiceMock.updateCompleteness).toHaveBeenCalledWith(10, { incomplete: true, comment: null });
  });

  it('toggleIncomplete() shows an error toast on failure', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    itemServiceMock.updateCompleteness.mockReturnValueOnce(throwError(() => new Error('server')));

    await component.toggleIncomplete(MOCK_ITEM);

    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('openCommentEditor()/cancelCommentEdit() toggle the inline editor state', () => {
    component.openCommentEditor(MOCK_ITEM);
    expect(component.commentEditId()).toBe(10);
    expect(component.commentDraft()).toBe('');
    component.cancelCommentEdit();
    expect(component.commentEditId()).toBeNull();
  });

  it('saveComment() persists the draft and closes the editor', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    component.openCommentEditor(MOCK_ITEM);
    component.commentDraft.set('Piece manquante');

    await component.saveComment(MOCK_ITEM);

    expect(itemServiceMock.updateCompleteness).toHaveBeenCalledWith(10, { incomplete: false, comment: 'Piece manquante' });
    expect(component.commentEditId()).toBeNull();
  });

  it('startEdit()/cancelEdit() control the editingItem signal', () => {
    component.startEdit(MOCK_ITEM);
    expect(component.editingItem()).toEqual(MOCK_ITEM);
    component.cancelEdit();
    expect(component.editingItem()).toBeNull();
  });

  it('onItemSaved() clears the editing item and reloads the list', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    component.startEdit(MOCK_ITEM);

    component.onItemSaved();
    await fixture.whenStable();

    expect(component.editingItem()).toBeNull();
    expect(itemServiceMock.getBySeller).toHaveBeenCalledWith(5);
  });

  it('confirmDelete() does nothing when the user cancels the dialog', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(false));
    await component.confirmDelete(MOCK_ITEM);
    expect(itemServiceMock.delete).not.toHaveBeenCalled();
  });

  it('confirmDelete() deletes the item and shows a success toast on confirm', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();

    await component.confirmDelete(MOCK_ITEM);

    expect(itemServiceMock.delete).toHaveBeenCalledWith(10);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('confirmDelete() shows an error toast on failure', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    itemServiceMock.delete.mockReturnValueOnce(throwError(() => new Error('server')));

    await component.confirmDelete(MOCK_ITEM);

    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('deselecting the seller clears the items list', async () => {
    const sellerSearch = getSellerSearch();
    sellerSearch.selectSeller(MOCK_SELLER);
    fixture.detectChanges();
    await fixture.whenStable();

    sellerSearch.changeSeller();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.items()).toEqual([]);
  });

  it('defaults to individual mode and switches to lot mode via setDepositMode()', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.depositMode()).toBe('individual');
    component.setDepositMode('lot');
    expect(component.depositMode()).toBe('lot');
  });

  it('selecting a new seller resets deposit mode back to individual', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    component.setDepositMode('lot');

    const otherSeller = { id: 6, firstName: 'Bruno', lastName: 'Durand', email: 'durand@email.com', phone: '0698765432' };
    getSellerSearch().changeSeller();
    getSellerSearch().selectSeller(otherSeller);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.depositMode()).toBe('individual');
  });

  it('onLotSaved() switches back to individual mode and reloads the list', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    component.setDepositMode('lot');

    component.onLotSaved();
    await fixture.whenStable();

    expect(component.depositMode()).toBe('individual');
    expect(itemServiceMock.getBySeller).toHaveBeenCalledWith(5);
  });

  it('renders the lot badge and lot price for items belonging to a lot, without any row actions', async () => {
    itemServiceMock.getBySeller.mockReturnValue(of([MOCK_LOT_ITEM]));
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const rowText = fixture.nativeElement.textContent as string;
    expect(rowText).toContain('Lot Jouets');
    const actionButtons = fixture.debugElement.queryAll(By.css('.article-row__actions button'));
    expect(actionButtons).toHaveLength(0);
  });

  function getValidateButton(): HTMLButtonElement {
    return fixture.debugElement.query(By.css('.validate-deposit-btn')).nativeElement as HTMLButtonElement;
  }

  it('validate deposit button is disabled when the item list is empty', async () => {
    itemServiceMock.getBySeller.mockReturnValue(of([]));
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getValidateButton().disabled).toBe(true);
  });

  it('validate deposit button is enabled once items exist', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(getValidateButton().disabled).toBe(false);
  });

  it('validateDeposit() does nothing when the user cancels the confirmation dialog', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    confirmDialogMock.open.mockReturnValueOnce(of(false));

    await component.validateDeposit();

    expect(depositServiceMock.validateDeposit).not.toHaveBeenCalled();
  });

  it('validateDeposit() calls the service with the selected seller id and shows a success toast', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();

    await component.validateDeposit();

    expect(depositServiceMock.validateDeposit).toHaveBeenCalledWith(5);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.validatingDeposit()).toBe(false);
  });

  it('validateDeposit() shows a dedicated toast when no thermal printer is available', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    depositServiceMock.validateDeposit.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 422, error: { type: 'https://pluribourse/errors/invalid-printer-selection' } }))
    );

    await component.validateDeposit();

    expect(toastMock.showError).toHaveBeenCalledWith('volunteer.deposit.error.printerUnavailable');
  });

  it('validateDeposit() shows a generic error toast on other failures', async () => {
    selectMockSeller();
    fixture.detectChanges();
    await fixture.whenStable();
    depositServiceMock.validateDeposit.mockReturnValueOnce(throwError(() => new Error('server')));

    await component.validateDeposit();

    expect(toastMock.showError).toHaveBeenCalledWith('volunteer.deposit.error.validate');
  });
});
