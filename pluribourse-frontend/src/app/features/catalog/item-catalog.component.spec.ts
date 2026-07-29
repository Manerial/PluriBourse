import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { Subject, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { ItemCatalogComponent } from './item-catalog.component';
import { ItemService } from '../../services/item.service';
import { CategoryService } from '../../services/category.service';
import { ItemCatalogDto, ItemCatalogPageResponse } from '../../models/item.model';
import { EditionCategoryDto } from '../../models/category.model';

const MOCK_ITEMS: ItemCatalogDto[] = [
  { id: 1, barcode: '0001-0001', name: 'Kapla', price: 5, incomplete: false, sold: false, categoryName: 'Jouets', tableNumber: 1, sellerFirstName: 'Alice', sellerLastName: 'Vendeuse', lotId: null, lotName: null },
  { id: 2, barcode: '0001-0002', name: 'Robot incomplet', price: 8, incomplete: true, sold: false, categoryName: 'Livres', tableNumber: 2, sellerFirstName: 'Alice', sellerLastName: 'Vendeuse', lotId: null, lotName: null },
];

const MOCK_PAGE: ItemCatalogPageResponse = {
  page: { content: MOCK_ITEMS, totalElements: 2, totalPages: 1, number: 0, size: 50 },
};

const MOCK_CATEGORIES: EditionCategoryDto[] = [
  { id: 1, name: 'Jouets', tableNumbers: [1] },
  { id: 2, name: 'Livres', tableNumbers: [2, 3] },
];

describe('ItemCatalogComponent', () => {
  let fixture: ComponentFixture<ItemCatalogComponent>;
  let component: ItemCatalogComponent;

  const itemServiceMock = {
    getCatalog: vi.fn().mockReturnValue(of(MOCK_PAGE)),
  };

  const categoryServiceMock = {
    getCategoriesForActiveEdition: vi.fn().mockReturnValue(of(MOCK_CATEGORIES)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    itemServiceMock.getCatalog.mockReturnValue(of(MOCK_PAGE));
    categoryServiceMock.getCategoriesForActiveEdition.mockReturnValue(of(MOCK_CATEGORIES));

    await TestBed.configureTestingModule({
      imports: [ItemCatalogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: ItemService, useValue: itemServiceMock },
        { provide: CategoryService, useValue: categoryServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ItemCatalogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('loads categories and the first page of items on init', () => {
    expect(categoryServiceMock.getCategoriesForActiveEdition).toHaveBeenCalledOnce();
    expect(itemServiceMock.getCatalog).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 50 }));
    expect(component.items().length).toBe(2);
    expect(component.totalElements()).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('computes table options as the sorted union of every category tableNumbers', () => {
    expect(component.tableOptions()).toEqual([1, 2, 3]);
  });

  it('onPageChange loads the requested page', async () => {
    await component.onPageChange({ pageIndex: 1, pageSize: 50, length: 2 } as PageEvent);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
    expect(component.pageIndex()).toBe(1);
  });

  it('discards a stale response that resolves after a newer request', async () => {
    const staleResponse = new Subject<ItemCatalogPageResponse>();
    const freshPage: ItemCatalogPageResponse = {
      page: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 },
    };
    itemServiceMock.getCatalog
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

  it('debounces text filter input before reloading', async () => {
    vi.useFakeTimers();
    component.onNameInput('Kapla');
    vi.advanceTimersByTime(299);
    expect(itemServiceMock.getCatalog).not.toHaveBeenCalledWith(expect.objectContaining({ name: 'Kapla' }));

    vi.advanceTimersByTime(1);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ name: 'Kapla', page: 0 }));
  });

  it('debounces the barcode and seller name filters the same way', async () => {
    vi.useFakeTimers();
    component.onBarcodeInput('0001');
    component.onSellerNameInput('Vendeuse');
    vi.advanceTimersByTime(300);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(
      expect.objectContaining({ barcode: '0001', sellerName: 'Vendeuse', page: 0 })
    );
  });

  it('category filter reloads immediately without debounce', async () => {
    await component.onCategoryChange(1);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ categoryId: 1, page: 0 }));
  });

  it('table filter reloads immediately without debounce', async () => {
    await component.onTableChange(2);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ tableNumber: 2, page: 0 }));
  });

  it('sold filter reloads immediately without debounce', async () => {
    await component.onSoldChange(true);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ sold: true, page: 0 }));
  });

  it('incomplete filter reloads immediately without debounce', async () => {
    await component.onIncompleteChange(true);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ incomplete: true, page: 0 }));
  });

  it('sorting ascending then descending toggles the sort param', async () => {
    await component.onSortChange({ active: 'name', direction: 'asc' } as Sort);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'name,asc' }));

    await component.onSortChange({ active: 'name', direction: 'desc' } as Sort);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ sort: 'name,desc' }));
  });

  it('clearing the sort direction omits the sort param', async () => {
    await component.onSortChange({ active: 'name', direction: '' } as Sort);
    expect(itemServiceMock.getCatalog).toHaveBeenLastCalledWith(expect.objectContaining({ sort: undefined }));
  });

  it('reflects an empty page returned by the backend', async () => {
    itemServiceMock.getCatalog.mockReturnValueOnce(
      of({ page: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 } })
    );
    await component.onPageChange({ pageIndex: 0, pageSize: 50, length: 0 } as PageEvent);
    expect(component.totalElements()).toBe(0);
    expect(component.items()).toEqual([]);
  });

  it('shows a dedicated error key when load fails because no edition is active', async () => {
    itemServiceMock.getCatalog.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ error: { type: 'https://pluribourse/errors/no-active-edition' }, status: 404 }))
    );
    await component.onPageChange({ pageIndex: 0, pageSize: 50, length: 0 } as PageEvent);
    expect(component.error()).toBe('catalog.error.noActiveEdition');
  });

  it('shows a generic error key when load fails for another reason', async () => {
    itemServiceMock.getCatalog.mockReturnValueOnce(throwError(() => new Error('network')));
    await component.onPageChange({ pageIndex: 0, pageSize: 50, length: 0 } as PageEvent);
    expect(component.error()).toBe('catalog.error.load');
  });

  it('renders the loading skeleton while isLoading is true', () => {
    component.isLoading.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-skeleton-row')).toBeTruthy();
  });

  it('renders the error notification when error is set', () => {
    component.isLoading.set(false);
    component.error.set('catalog.error.load');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-notification-inline')).toBeTruthy();
  });

  it('renders the empty state when no items match the current filters', async () => {
    itemServiceMock.getCatalog.mockReturnValueOnce(
      of({ page: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 } })
    );
    await component.onPageChange({ pageIndex: 0, pageSize: 50, length: 0 } as PageEvent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-empty-state')).toBeTruthy();
  });

  it('falls back to an empty category list when the category lookup fails', async () => {
    categoryServiceMock.getCategoriesForActiveEdition.mockReturnValueOnce(throwError(() => new Error('network')));
    fixture = TestBed.createComponent(ItemCatalogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.categories()).toEqual([]);
    expect(component.tableOptions()).toEqual([]);
  });
});
