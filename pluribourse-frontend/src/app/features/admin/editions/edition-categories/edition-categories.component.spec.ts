import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EditionCategoriesComponent } from './edition-categories.component';
import { EditionService } from '../../../../services/edition.service';
import { CategoryService } from '../../../../services/category.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { EditionDto } from '../../../../models/edition.model';
import { EditionCategoryDto } from '../../../../models/category.model';
import { Language } from '../../../../models/language.enum';

const MOCK_EDITION_PREP: EditionDto = {
  id: 1, name: 'Bourse 2026', phase: 'PREPARATION',
  commissionRate: 20, documentLanguage: Language.EN, createdAt: '2026-01-01',
  archived: false, startDate: '2026-06-01', endDate: '2026-06-03', currency: '€'
};

const MOCK_EDITION_DEPOSIT: EditionDto = {
  ...MOCK_EDITION_PREP, phase: 'DEPOSIT'
};

const MOCK_CLOSED: EditionDto = {
  id: 2, name: 'Bourse 2025', phase: 'CLOSED',
  commissionRate: 20, documentLanguage: Language.EN, createdAt: '2025-01-01',
  archived: false, startDate: '2026-06-01', endDate: '2026-06-03', currency: '€'
};

const MOCK_CATEGORY: EditionCategoryDto = { id: 1, name: 'Jouets', tableNumbers: [1, 2] };

type Row = { id: number | null; name: string; tableInput: string; nameTouched: boolean; tableTouched: boolean };
const makeRow = (over: Partial<Row> = {}): Row => ({
  id: null, name: 'Jouets', tableInput: '1, 2', nameTouched: false, tableTouched: false, ...over,
});

describe('EditionCategoriesComponent', () => {
  let fixture: ComponentFixture<EditionCategoriesComponent>;
  let component: EditionCategoriesComponent;

  const editionServiceMock = {
    getById: vi.fn().mockReturnValue(of(MOCK_EDITION_PREP)),
    getAll: vi.fn().mockReturnValue(of([MOCK_EDITION_PREP, MOCK_CLOSED])),
  };
  const categoryServiceMock = {
    getCategories: vi.fn().mockReturnValue(of([MOCK_CATEGORY])),
    saveCategories: vi.fn().mockReturnValue(of([MOCK_CATEGORY])),
    copyFromEdition: vi.fn().mockReturnValue(of([MOCK_CATEGORY])),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const dialogRefMock = { close: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION_PREP));
    editionServiceMock.getAll.mockReturnValue(of([MOCK_EDITION_PREP, MOCK_CLOSED]));
    categoryServiceMock.getCategories.mockReturnValue(of([MOCK_CATEGORY]));

    await TestBed.configureTestingModule({
      imports: [EditionCategoriesComponent],
      providers: [
        provideAnimations(),
        provideTranslateService({ lang: 'en' }),
        { provide: DIALOG_DATA, useValue: { editionId: 1 } },
        { provide: DialogRef, useValue: dialogRefMock },
        { provide: EditionService, useValue: editionServiceMock },
        { provide: CategoryService, useValue: categoryServiceMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    TestBed.inject(TranslateService).setTranslation('en', {
      edition: { phase: { DEPOSIT: 'Deposit', SALE: 'Sale' } },
    });

    fixture = TestBed.createComponent(EditionCategoriesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads categories on init', () => {
    expect(categoryServiceMock.getCategories).toHaveBeenCalledWith(1);
    expect(component.categories()).toHaveLength(1);
    expect(component.categories()[0].name).toBe('Jouets');
  });

  it('loads closed editions on init (excluding current)', () => {
    const closed = component.closedEditions();
    expect(closed).toHaveLength(1);
    expect(closed[0].id).toBe(2);
  });

  it('isReadOnly is false in PREPARATION phase', () => {
    expect(component.isReadOnly()).toBe(false);
  });

  it('isReadOnly is true in DEPOSIT phase', async () => {
    editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION_DEPOSIT));
    await component.ngOnInit();
    expect(component.isReadOnly()).toBe(true);
  });

  it('lockedPhaseLabel reflects the current edition phase', async () => {
    editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION_DEPOSIT));
    await component.ngOnInit();
    expect(component.lockedPhaseLabel()).toBe('Deposit');
  });

  it('lockedPhaseLabel reflects a different phase (not hardcoded to Deposit)', async () => {
    editionServiceMock.getById.mockReturnValue(of({ ...MOCK_EDITION_DEPOSIT, phase: 'SALE' }));
    await component.ngOnInit();
    expect(component.lockedPhaseLabel()).toBe('Sale');
  });

  it('addCategory pushes a new empty row', () => {
    const before = component.categories().length;
    component.addCategory();
    expect(component.categories().length).toBe(before + 1);
    expect(component.categories()[before].name).toBe('');
  });

  it('removeCategory removes the row at given index', () => {
    component.addCategory();
    const before = component.categories().length;
    component.removeCategory(0);
    expect(component.categories().length).toBe(before - 1);
  });

  it('closedEditions is empty when no closed editions exist', async () => {
    editionServiceMock.getAll.mockReturnValue(of([MOCK_EDITION_PREP]));
    await component.ngOnInit();
    expect(component.closedEditions()).toHaveLength(0);
  });

  it('sets error key when load fails', async () => {
    editionServiceMock.getById.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('category.load.error');
  });

  it('onSave calls saveCategories with parsed tableNumbers and shows success toast', async () => {
    component.categories.set([makeRow({ tableInput: '1, 2' })]);
    await component.onSave();
    expect(categoryServiceMock.saveCategories).toHaveBeenCalledWith(1, [
      { id: null, name: 'Jouets', tableNumbers: [1, 2] },
    ]);
    expect(toastMock.showSuccess).toHaveBeenCalled();
    expect(component.categories()[0].name).toBe('Jouets');
  });

  it('onSave blocks save and flags the name field as invalid when name is blank', async () => {
    component.categories.set([makeRow({ name: '' })]);
    await component.onSave();
    expect(categoryServiceMock.saveCategories).not.toHaveBeenCalled();
    expect(component.isNameInvalid(component.categories()[0])).toBe(true);
  });

  it('onSave blocks save and flags the tables field as invalid when no tables assigned', async () => {
    component.categories.set([makeRow({ tableInput: '' })]);
    await component.onSave();
    expect(categoryServiceMock.saveCategories).not.toHaveBeenCalled();
    expect(component.isTableInvalid(component.categories()[0])).toBe(true);
  });

  it('clears the name error as soon as a name is typed, without another save attempt', async () => {
    component.categories.set([makeRow({ name: '' })]);
    await component.onSave();
    expect(component.isNameInvalid(component.categories()[0])).toBe(true);

    component.categories()[0].name = 'Livres';
    expect(component.isNameInvalid(component.categories()[0])).toBe(false);
  });

  it('shows the required error after the field is blurred, before any save', () => {
    component.categories.set([makeRow({ name: '', tableInput: '' })]);
    const row = component.categories()[0];
    expect(component.isNameInvalid(row)).toBe(false);
    expect(component.isTableInvalid(row)).toBe(false);

    component.markNameTouched(row);
    expect(component.isNameInvalid(row)).toBe(true);
    expect(component.isTableInvalid(row)).toBe(false);

    component.markTableTouched(row);
    expect(component.isTableInvalid(row)).toBe(true);
  });

  it('does not flag fields as invalid in read-only mode', async () => {
    editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION_DEPOSIT));
    await component.ngOnInit();
    component.categories.set([makeRow({ name: '', tableInput: '', nameTouched: true, tableTouched: true })]);
    const row = component.categories()[0];
    expect(component.isNameInvalid(row)).toBe(false);
    expect(component.isTableInvalid(row)).toBe(false);
  });

  it('onSave shows error toast when service fails', async () => {
    categoryServiceMock.saveCategories.mockReturnValue(throwError(() => new Error('server')));
    component.categories.set([makeRow({ tableInput: '1' })]);
    await component.onSave();
    expect(toastMock.showError).toHaveBeenCalled();
  });

  it('onSave closes the dialog after a successful save', async () => {
    categoryServiceMock.saveCategories.mockReturnValue(of([MOCK_CATEGORY]));
    component.categories.set([makeRow({ tableInput: '1, 2' })]);
    await component.onSave();
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
  });

  it('onSave does not close the dialog when the service fails', async () => {
    categoryServiceMock.saveCategories.mockReturnValue(throwError(() => new Error('server')));
    component.categories.set([makeRow({ tableInput: '1' })]);
    await component.onSave();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('onSave does not close the dialog when validation fails', async () => {
    component.categories.set([makeRow({ name: '' })]);
    await component.onSave();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('onCopy calls copyFromEdition and replaces categories with success toast', async () => {
    component.onSelectSource(2);
    await component.onCopy();
    expect(categoryServiceMock.copyFromEdition).toHaveBeenCalledWith(1, 2);
    expect(component.categories()).toHaveLength(1);
    expect(component.categories()[0].name).toBe('Jouets');
    expect(toastMock.showSuccess).toHaveBeenCalled();
  });

  it('onCopy shows error toast when copy service fails', async () => {
    categoryServiceMock.copyFromEdition.mockReturnValue(throwError(() => new Error('server')));
    component.onSelectSource(2);
    await component.onCopy();
    expect(toastMock.showError).toHaveBeenCalled();
  });
});
