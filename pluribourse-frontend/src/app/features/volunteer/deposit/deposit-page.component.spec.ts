import { TestBed, ComponentFixture } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { DepositPageComponent } from './deposit-page.component';
import { SellerSearchComponent } from './seller-search.component';
import { CategoryService } from '../../../services/category.service';
import { ItemService } from '../../../services/item.service';
import { SellerService } from '../../../services/seller.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionCategoryDto } from '../../../models/category.model';
import { ItemDto } from '../../../models/item.model';
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
};

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
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const confirmDialogMock = { open: vi.fn().mockReturnValue(of(true)) };

  beforeEach(async () => {
    vi.clearAllMocks();
    categoryServiceMock.getCategoriesForActiveEdition.mockReturnValue(of(MOCK_CATEGORIES));
    itemServiceMock.getBySeller.mockReturnValue(of([MOCK_ITEM]));
    itemServiceMock.updateCompleteness.mockReturnValue(of(MOCK_ITEM));
    itemServiceMock.delete.mockReturnValue(of(undefined));
    sellerServiceMock.search.mockReturnValue(of([]));
    confirmDialogMock.open.mockReturnValue(of(true));

    await TestBed.configureTestingModule({
      imports: [DepositPageComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: CategoryService, useValue: categoryServiceMock },
        { provide: ItemService, useValue: itemServiceMock },
        { provide: SellerService, useValue: sellerServiceMock },
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
});
