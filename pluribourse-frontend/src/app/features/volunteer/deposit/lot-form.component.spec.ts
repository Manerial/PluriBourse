import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { LotFormComponent } from './lot-form.component';
import { LotService } from '../../../services/lot.service';
import { EditionCategoryDto } from '../../../models/category.model';
import { LotDto } from '../../../models/lot.model';

const MOCK_CATEGORIES: EditionCategoryDto[] = [
  { id: 1, name: 'Jouets', tableNumbers: [1, 2] },
  { id: 2, name: 'Livres', tableNumbers: [2, 3] },
];

const MOCK_LOT: LotDto = {
  id: 20,
  name: 'Lot Jouets',
  globalPrice: 15,
  categoryId: 1,
  categoryName: 'Jouets',
  items: [
    {
      id: 100,
      sellerProfileId: 5,
      categoryId: 1,
      categoryName: 'Jouets',
      name: 'Piece A',
      price: null,
      incomplete: false,
      comment: null,
      tableNumber: 1,
      lotId: 20,
      lotName: 'Lot Jouets',
      lotPrice: 15,
    },
    {
      id: 101,
      sellerProfileId: 5,
      categoryId: 1,
      categoryName: 'Jouets',
      name: 'Piece B',
      price: null,
      incomplete: false,
      comment: null,
      tableNumber: 1,
      lotId: 20,
      lotName: 'Lot Jouets',
      lotPrice: 15,
    },
  ],
};

describe('LotFormComponent', () => {
  let fixture: ComponentFixture<LotFormComponent>;
  let component: LotFormComponent;

  const lotServiceMock = {
    create: vi.fn().mockReturnValue(of(MOCK_LOT)),
    update: vi.fn().mockReturnValue(of(MOCK_LOT)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    lotServiceMock.create.mockReturnValue(of(MOCK_LOT));
    lotServiceMock.update.mockReturnValue(of(MOCK_LOT));

    await TestBed.configureTestingModule({
      imports: [LotFormComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: LotService, useValue: lotServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(LotFormComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('sellerId', 5);
    fixture.componentRef.setInput('categories', MOCK_CATEGORIES);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('starts with 2 empty item rows and an invalid form', () => {
    expect(component.itemsFormArray.length).toBe(2);
    expect(component.form.invalid).toBe(true);
  });

  it('addItemRow() adds a row to the form array', () => {
    component.addItemRow();
    expect(component.itemsFormArray.length).toBe(3);
  });

  it('removeItemRow() removes a row when more than 2 remain', () => {
    component.addItemRow();
    component.removeItemRow(0);
    expect(component.itemsFormArray.length).toBe(2);
  });

  it('removeItemRow() does nothing when only 2 rows remain', () => {
    component.removeItemRow(0);
    expect(component.itemsFormArray.length).toBe(2);
  });

  it('does not call create when the form is invalid', async () => {
    await component.onSubmit();
    expect(lotServiceMock.create).not.toHaveBeenCalled();
  });

  it('calls create with the assembled payload and emits saved', async () => {
    fillValidForm(component);
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    await component.onSubmit();

    expect(lotServiceMock.create).toHaveBeenCalledWith({
      sellerProfileId: 5,
      categoryId: 1,
      name: 'Lot Jouets',
      globalPrice: 15,
      items: [
        { name: 'Piece A', incomplete: false, comment: null },
        { name: 'Piece B', incomplete: false, comment: null },
      ],
    });
    expect(savedSpy).toHaveBeenCalledWith(MOCK_LOT);
  });

  it('does not call create when categoryId is missing, even with valid items', async () => {
    component.form.controls.name.setValue('Lot Jouets');
    component.form.controls.globalPrice.setValue(15);
    component.itemsFormArray.at(0).setValue({ id: null, name: 'Piece A', incomplete: false, comment: '' });
    component.itemsFormArray.at(1).setValue({ id: null, name: 'Piece B', incomplete: false, comment: '' });

    await component.onSubmit();

    expect(lotServiceMock.create).not.toHaveBeenCalled();
  });

  it('resets the form after a successful create', async () => {
    fillValidForm(component);

    await component.onSubmit();

    expect(component.form.controls.name.value).toBe('');
    expect(component.itemsFormArray.length).toBe(2);
  });

  it('sets phaseLocked error key when create fails with item-modification-locked', async () => {
    lotServiceMock.create.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 422, error: { type: 'https://pluribourse/errors/item-modification-locked' } }))
    );
    fillValidForm(component);

    await component.onSubmit();

    expect(component.error()).toBe('volunteer.deposit.item.lotForm.error.phaseLocked');
    expect(component.loading()).toBe(false);
  });

  it('sets generic save error key on unexpected failure', async () => {
    lotServiceMock.create.mockReturnValue(throwError(() => new Error('server')));
    fillValidForm(component);

    await component.onSubmit();

    expect(component.error()).toBe('volunteer.deposit.item.lotForm.error.save');
  });

  it('cancel() emits cancelled', () => {
    const cancelledSpy = vi.fn();
    component.cancelled.subscribe(cancelledSpy);
    component.cancel();
    expect(cancelledSpy).toHaveBeenCalled();
  });

  describe('editing an existing lot', () => {
    beforeEach(async () => {
      fixture.componentRef.setInput('editingLot', MOCK_LOT);
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('prefills the form and marks isEditing as true', () => {
      expect(component.isEditing()).toBe(true);
      expect(component.form.controls.name.value).toBe('Lot Jouets');
      expect(component.form.controls.globalPrice.value).toBe(15);
      expect(component.form.controls.categoryId.value).toBe(1);
      expect(component.itemsFormArray.length).toBe(2);
      expect(component.itemsFormArray.at(0).value).toEqual({
        id: 100,
        name: 'Piece A',
        incomplete: false,
        comment: '',
      });
    });

    it('calls update with existing ids preserved and null id for a newly added row', async () => {
      component.addItemRow();
      component.itemsFormArray.at(2).setValue({ id: null, name: 'Piece C', incomplete: true, comment: 'Neuve' });

      await component.onSubmit();

      expect(lotServiceMock.update).toHaveBeenCalledWith(20, {
        categoryId: 1,
        name: 'Lot Jouets',
        globalPrice: 15,
        items: [
          { id: 100, name: 'Piece A', incomplete: false, comment: null },
          { id: 101, name: 'Piece B', incomplete: false, comment: null },
          { id: null, name: 'Piece C', incomplete: true, comment: 'Neuve' },
        ],
      });
      expect(lotServiceMock.create).not.toHaveBeenCalled();
    });

    it('does not reset the form after a successful update', async () => {
      await component.onSubmit();

      expect(component.form.controls.name.value).toBe('Lot Jouets');
      expect(component.itemsFormArray.length).toBe(2);
    });

    it('switching back to creation mode resets the form to two empty rows', async () => {
      fixture.componentRef.setInput('editingLot', null);
      fixture.detectChanges();
      await fixture.whenStable();

      expect(component.isEditing()).toBe(false);
      expect(component.form.controls.name.value).toBe('');
      expect(component.itemsFormArray.length).toBe(2);
      expect(component.itemsFormArray.at(0).value.id).toBeNull();
    });
  });

  function fillValidForm(cmp: LotFormComponent): void {
    cmp.form.controls.name.setValue('Lot Jouets');
    cmp.form.controls.globalPrice.setValue(15);
    cmp.form.controls.categoryId.setValue(1);
    cmp.itemsFormArray.at(0).setValue({ id: null, name: 'Piece A', incomplete: false, comment: '' });
    cmp.itemsFormArray.at(1).setValue({ id: null, name: 'Piece B', incomplete: false, comment: '' });
  }
});
