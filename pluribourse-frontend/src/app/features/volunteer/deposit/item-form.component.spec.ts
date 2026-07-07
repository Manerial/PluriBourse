import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { ItemFormComponent } from './item-form.component';
import { ItemService } from '../../../services/item.service';
import { EditionCategoryDto } from '../../../models/category.model';
import { ItemDto } from '../../../models/item.model';

const MOCK_CATEGORIES: EditionCategoryDto[] = [
  { id: 1, name: 'Jouets', tableNumbers: [1, 2] },
  { id: 2, name: 'Livres', tableNumbers: [2, 3] },
];

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

describe('ItemFormComponent', () => {
  let fixture: ComponentFixture<ItemFormComponent>;
  let component: ItemFormComponent;

  const itemServiceMock = {
    create: vi.fn().mockReturnValue(of(MOCK_ITEM)),
    update: vi.fn().mockReturnValue(of(MOCK_ITEM)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    itemServiceMock.create.mockReturnValue(of(MOCK_ITEM));
    itemServiceMock.update.mockReturnValue(of(MOCK_ITEM));

    await TestBed.configureTestingModule({
      imports: [ItemFormComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: ItemService, useValue: itemServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ItemFormComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('sellerId', 5);
    fixture.componentRef.setInput('categories', MOCK_CATEGORIES);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('form is invalid when required fields are empty', () => {
    expect(component.form.invalid).toBe(true);
  });

  it('form is invalid when price is below the minimum', () => {
    component.form.controls.name.setValue('Kapla');
    component.form.controls.price.setValue(0);
    component.form.controls.categoryId.setValue(1);
    expect(component.form.invalid).toBe(true);
  });

  it('form is invalid when name and price are set but no category is selected', () => {
    // Regression test: categoryId used to default to 0, which Validators.required does not
    // reject, letting a category-less item slip through as "valid".
    component.form.controls.name.setValue('Kapla');
    component.form.controls.price.setValue(5);
    expect(component.form.controls.categoryId.value).toBeNull();
    expect(component.form.invalid).toBe(true);
  });

  it('does not call create when the form is invalid', async () => {
    await component.onSubmit();
    expect(itemServiceMock.create).not.toHaveBeenCalled();
  });

  it('calls create with sellerId and emits saved with the assigned table number', async () => {
    component.form.controls.name.setValue('Kapla');
    component.form.controls.price.setValue(5);
    component.form.controls.categoryId.setValue(1);
    const savedSpy = vi.fn();
    component.saved.subscribe(savedSpy);

    await component.onSubmit();

    expect(itemServiceMock.create).toHaveBeenCalledWith({
      sellerProfileId: 5,
      categoryId: 1,
      name: 'Kapla',
      price: 5,
      incomplete: false,
      comment: null,
    });
    expect(savedSpy).toHaveBeenCalledWith(MOCK_ITEM);
    expect(component.assignedTableNumber()).toBe(1);
  });

  it('resets the form after a successful create', async () => {
    component.form.controls.name.setValue('Kapla');
    component.form.controls.price.setValue(5);
    component.form.controls.categoryId.setValue(1);

    await component.onSubmit();

    expect(component.form.controls.name.value).toBe('');
  });

  it('sets error key when create fails', async () => {
    itemServiceMock.create.mockReturnValue(throwError(() => new Error('server')));
    component.form.controls.name.setValue('Kapla');
    component.form.controls.price.setValue(5);
    component.form.controls.categoryId.setValue(1);

    await component.onSubmit();

    expect(component.error()).toBe('volunteer.deposit.item.form.error.save');
    expect(component.loading()).toBe(false);
  });

  it('pre-fills the form and switches to edit mode when editingItem is set', async () => {
    fixture.componentRef.setInput('editingItem', MOCK_ITEM);
    await fixture.whenStable();

    expect(component.isEditing()).toBe(true);
    expect(component.form.controls.name.value).toBe('Kapla');
    expect(component.form.controls.price.value).toBe(5);
    expect(component.form.controls.categoryId.value).toBe(1);
  });

  it('calls update instead of create when editing, without resetting the form', async () => {
    fixture.componentRef.setInput('editingItem', MOCK_ITEM);
    await fixture.whenStable();
    component.form.controls.name.setValue('Kapla neuf');

    await component.onSubmit();

    expect(itemServiceMock.update).toHaveBeenCalledWith(10, {
      sellerProfileId: 5,
      categoryId: 1,
      name: 'Kapla neuf',
      price: 5,
      incomplete: false,
      comment: null,
    });
    expect(itemServiceMock.create).not.toHaveBeenCalled();
    expect(component.form.controls.name.value).toBe('Kapla neuf');
  });

  it('cancel() emits cancelled', () => {
    const cancelledSpy = vi.fn();
    component.cancelled.subscribe(cancelledSpy);
    component.cancel();
    expect(cancelledSpy).toHaveBeenCalled();
  });
});
