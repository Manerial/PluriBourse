import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { Subject, of } from 'rxjs';
import { vi } from 'vitest';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { ArchivedCatalogComponent } from './archived-catalog.component';
import { ArchivedItemService } from '../../../services/archived-item.service';
import { CategoryService } from '../../../services/category.service';
import { EditionService } from '../../../services/edition.service';
import { ArchivedItemDto, ArchivedItemPageResponse } from '../../../models/archived-item.model';
import { EditionCategoryDto } from '../../../models/category.model';
import { EditionDto } from '../../../models/edition.model';
import { Language } from '../../../models/language.enum';

const MOCK_EDITIONS: EditionDto[] = [
  { id: 1, name: 'Bourse 2025', phase: 'CLOSED', commissionRate: 10, documentLanguage: Language.FR, createdAt: '2025-01-01', archived: true, startDate: '2025-03-01', endDate: '2025-03-03', currency: '$' },
  { id: 2, name: 'Bourse 2024', phase: 'CLOSED', commissionRate: 10, documentLanguage: Language.FR, createdAt: '2024-01-01', archived: true, startDate: '2024-03-01', endDate: '2024-03-03', currency: '€' },
  { id: 3, name: 'Bourse Active', phase: 'DEPOSIT', commissionRate: 10, documentLanguage: Language.FR, createdAt: '2026-01-01', archived: false, startDate: '2026-03-01', endDate: '2026-03-03', currency: '€' },
];

const MOCK_ITEMS: ArchivedItemDto[] = [
  { id: 1, name: 'Kapla', categoryName: 'Jouets', sold: true, price: 5 },
  { id: 2, name: 'Robot', categoryName: 'Livres', sold: false, price: 12.5 },
];

const MOCK_PAGE: ArchivedItemPageResponse = {
  page: { content: MOCK_ITEMS, totalElements: 2, totalPages: 1, number: 0, size: 50 },
};

const MOCK_CATEGORIES: EditionCategoryDto[] = [
  { id: 1, name: 'Jouets', tableNumbers: [1] },
  { id: 2, name: 'Livres', tableNumbers: [2] },
];

describe('ArchivedCatalogComponent', () => {
  let fixture: ComponentFixture<ArchivedCatalogComponent>;
  let component: ArchivedCatalogComponent;

  const editionServiceMock = {
    getAll: vi.fn().mockReturnValue(of(MOCK_EDITIONS)),
  };

  const categoryServiceMock = {
    getCategories: vi.fn().mockReturnValue(of(MOCK_CATEGORIES)),
  };

  const archivedItemServiceMock = {
    getArchivedCatalog: vi.fn().mockReturnValue(of(MOCK_PAGE)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getAll.mockReturnValue(of(MOCK_EDITIONS));
    categoryServiceMock.getCategories.mockReturnValue(of(MOCK_CATEGORIES));
    archivedItemServiceMock.getArchivedCatalog.mockReturnValue(of(MOCK_PAGE));

    await TestBed.configureTestingModule({
      imports: [ArchivedCatalogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: EditionService, useValue: editionServiceMock },
        { provide: CategoryService, useValue: categoryServiceMock },
        { provide: ArchivedItemService, useValue: archivedItemServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ArchivedCatalogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('lists only archived editions, sorted by start date descending', () => {
    expect(component.archivedEditions().map((e) => e.id)).toEqual([1, 2]);
  });

  it('does not load any items before an edition is selected', () => {
    expect(archivedItemServiceMock.getArchivedCatalog).not.toHaveBeenCalled();
    expect(component.items()).toEqual([]);
  });

  it('selecting an edition loads categories and the first page of items', async () => {
    await component.onEditionChange(1);

    expect(categoryServiceMock.getCategories).toHaveBeenCalledWith(1);
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenCalledWith(1, expect.objectContaining({ page: 0, size: 50 }));
    expect(component.items().length).toBe(2);
    expect(component.totalElements()).toBe(2);
    // Story 2.9: currency is resolved from the selected edition, not hardcoded.
    expect(component.currency()).toBe('$');
  });

  it('name filter reloads at page 0 after debounce', async () => {
    await component.onEditionChange(1);
    vi.useFakeTimers();
    component.onNameInput('Kapla');
    vi.advanceTimersByTime(299);
    expect(archivedItemServiceMock.getArchivedCatalog).not.toHaveBeenLastCalledWith(1, expect.objectContaining({ name: 'Kapla' }));

    vi.advanceTimersByTime(1);
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenLastCalledWith(1, expect.objectContaining({ name: 'Kapla', page: 0 }));
  });

  it('category filter reloads immediately at page 0', async () => {
    await component.onEditionChange(1);
    await component.onCategoryChange('Jouets');
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenLastCalledWith(1, expect.objectContaining({ categoryName: 'Jouets', page: 0 }));
  });

  it('sold filter reloads immediately at page 0', async () => {
    await component.onEditionChange(1);
    await component.onSoldChange(true);
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenLastCalledWith(1, expect.objectContaining({ sold: true, page: 0 }));
  });

  it('sort header click toggles ascending then descending', async () => {
    await component.onEditionChange(1);
    await component.onSortChange({ active: 'name', direction: 'asc' } as Sort);
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenLastCalledWith(1, expect.objectContaining({ sort: 'name,asc' }));

    await component.onSortChange({ active: 'name', direction: 'desc' } as Sort);
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenLastCalledWith(1, expect.objectContaining({ sort: 'name,desc' }));
  });

  it('pagination event loads the requested page', async () => {
    await component.onEditionChange(1);
    await component.onPageChange({ pageIndex: 1, pageSize: 50, length: 2 } as PageEvent);
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenLastCalledWith(1, expect.objectContaining({ page: 1 }));
    expect(component.pageIndex()).toBe(1);
  });

  it('switching edition resets filters and reloads the new edition at page 0', async () => {
    await component.onEditionChange(1);
    await component.onCategoryChange('Jouets');

    await component.onEditionChange(2);

    expect(component.categoryNameFilter()).toBeNull();
    expect(categoryServiceMock.getCategories).toHaveBeenLastCalledWith(2);
    expect(archivedItemServiceMock.getArchivedCatalog).toHaveBeenLastCalledWith(2, expect.objectContaining({ categoryName: undefined, page: 0 }));
  });

  it('discards a stale response that resolves after a newer request', async () => {
    await component.onEditionChange(1);

    const staleResponse = new Subject<ArchivedItemPageResponse>();
    const freshPage: ArchivedItemPageResponse = {
      page: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 },
    };
    archivedItemServiceMock.getArchivedCatalog
      .mockReturnValueOnce(staleResponse.asObservable())
      .mockReturnValueOnce(of(freshPage));

    const stalePromise = component.onPageChange({ pageIndex: 1, pageSize: 50, length: 2 } as PageEvent);
    const freshPromise = component.onPageChange({ pageIndex: 2, pageSize: 50, length: 2 } as PageEvent);
    await freshPromise;
    expect(component.totalElements()).toBe(0);
    expect(component.pageIndex()).toBe(2);

    staleResponse.next(MOCK_PAGE);
    staleResponse.complete();
    await stalePromise;

    expect(component.totalElements()).toBe(0);
    expect(component.pageIndex()).toBe(2);
  });

  it('renders the no-selection empty state before any edition is chosen', () => {
    expect(fixture.nativeElement.querySelector('app-empty-state')).toBeTruthy();
  });

  it('renders the no-results empty state when the selected edition has no matching items', async () => {
    archivedItemServiceMock.getArchivedCatalog.mockReturnValueOnce(
      of({ page: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 } })
    );
    await component.onEditionChange(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state')).toBeTruthy();
  });
});
