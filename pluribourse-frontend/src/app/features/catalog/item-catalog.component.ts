import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { TranslatePipe } from '@ngx-translate/core';
import { Subject, debounceTime, firstValueFrom } from 'rxjs';
import { EditionCategoryDto } from '../../models/category.model';
import { CatalogFilter, ItemCatalogDto } from '../../models/item.model';
import { CategoryService } from '../../services/category.service';
import { CurrentEditionService } from '../../services/current-edition.service';
import { ItemService } from '../../services/item.service';
import { EmptyStateComponent } from '../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../shared/components/notification-inline/notification-inline.component';
import { SkeletonRowComponent } from '../../shared/components/skeleton-row/skeleton-row.component';
import { extractErrorType } from '../../shared/http-error.util';

const DEFAULT_PAGE_SIZE = 50;
const TEXT_FILTER_DEBOUNCE_MS = 300;

type TriState = boolean | null;

@Component({
  selector: 'app-item-catalog',
  standalone: true,
  imports: [
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatPaginatorModule,
    MatSortModule,
    TranslatePipe,
    EmptyStateComponent,
    NotificationInlineComponent,
    SkeletonRowComponent,
  ],
  templateUrl: './item-catalog.component.html',
  styleUrl: './item-catalog.component.scss',
})
export class ItemCatalogComponent implements OnInit {
  private readonly itemService = inject(ItemService);
  private readonly categoryService = inject(CategoryService);
  private readonly currentEditionService = inject(CurrentEditionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly currency = computed(() => this.currentEditionService.currentEdition()?.currency);

  readonly items = signal<ItemCatalogDto[]>([]);
  readonly totalElements = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = DEFAULT_PAGE_SIZE;
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);

  readonly categories = signal<EditionCategoryDto[]>([]);
  readonly tableOptions = computed<number[]>(() =>
    Array.from(new Set(this.categories().flatMap((category) => category.tableNumbers))).sort((a, b) => a - b)
  );

  readonly nameFilter = signal('');
  readonly barcodeFilter = signal('');
  readonly categoryIdFilter = signal<number | null>(null);
  readonly tableNumberFilter = signal<number | null>(null);
  readonly soldFilter = signal<TriState>(null);
  readonly incompleteFilter = signal<TriState>(null);
  readonly sellerNameFilter = signal('');

  readonly sortField = signal<string | undefined>(undefined);
  readonly sortDirection = signal<'asc' | 'desc' | ''>('');

  private readonly textFilterChanged = new Subject<void>();
  private requestSequence = 0;

  constructor() {
    this.textFilterChanged
      .pipe(debounceTime(TEXT_FILTER_DEBOUNCE_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        void this.loadPage(0);
      });
  }

  async ngOnInit(): Promise<void> {
    await this.loadCategories();
    await this.loadPage(0);
  }

  onNameInput(value: string): void {
    this.nameFilter.set(value);
    this.textFilterChanged.next();
  }

  onBarcodeInput(value: string): void {
    this.barcodeFilter.set(value);
    this.textFilterChanged.next();
  }

  onSellerNameInput(value: string): void {
    this.sellerNameFilter.set(value);
    this.textFilterChanged.next();
  }

  async onCategoryChange(value: number | null): Promise<void> {
    this.categoryIdFilter.set(value);
    await this.loadPage(0);
  }

  async onTableChange(value: number | null): Promise<void> {
    this.tableNumberFilter.set(value);
    await this.loadPage(0);
  }

  async onSoldChange(value: TriState): Promise<void> {
    this.soldFilter.set(value);
    await this.loadPage(0);
  }

  async onIncompleteChange(value: TriState): Promise<void> {
    this.incompleteFilter.set(value);
    await this.loadPage(0);
  }

  async onPageChange(event: PageEvent): Promise<void> {
    await this.loadPage(event.pageIndex);
  }

  async onSortChange(sort: Sort): Promise<void> {
    this.sortField.set(sort.direction ? sort.active : undefined);
    this.sortDirection.set(sort.direction);
    await this.loadPage(0);
  }

  private async loadCategories(): Promise<void> {
    try {
      const categories = await firstValueFrom(this.categoryService.getCategoriesForActiveEdition());
      this.categories.set(categories);
    } catch {
      this.categories.set([]);
    }
  }

  private async loadPage(page: number): Promise<void> {
    // Guards against out-of-order responses: rapid filter/sort/page changes can fire overlapping
    // requests, and network timing gives no guarantee the last one sent resolves last. Only the
    // response for the most recently issued request is applied; older ones are discarded.
    const requestId = ++this.requestSequence;
    this.isLoading.set(true);
    this.error.set(null);
    const filter: CatalogFilter = {
      name: this.nameFilter().trim() || undefined,
      barcode: this.barcodeFilter().trim() || undefined,
      categoryId: this.categoryIdFilter() ?? undefined,
      tableNumber: this.tableNumberFilter() ?? undefined,
      sold: this.soldFilter() ?? undefined,
      incomplete: this.incompleteFilter() ?? undefined,
      sellerName: this.sellerNameFilter().trim() || undefined,
      page,
      size: this.pageSize,
      sort: this.buildSort(),
    };
    try {
      const result = await firstValueFrom(this.itemService.getCatalog(filter));
      if (requestId !== this.requestSequence) {
        return;
      }
      this.items.set(result.page.content);
      this.totalElements.set(result.page.totalElements);
      this.pageIndex.set(page);
    } catch (err: unknown) {
      if (requestId !== this.requestSequence) {
        return;
      }
      const errorType = err instanceof HttpErrorResponse ? extractErrorType(err) : undefined;
      this.error.set(errorType?.endsWith('/no-active-edition') ? 'catalog.error.noActiveEdition' : 'catalog.error.load');
    } finally {
      if (requestId === this.requestSequence) {
        this.isLoading.set(false);
      }
    }
  }

  private buildSort(): string | undefined {
    const field = this.sortField();
    const direction = this.sortDirection();
    return field && direction ? `${field},${direction}` : undefined;
  }
}
