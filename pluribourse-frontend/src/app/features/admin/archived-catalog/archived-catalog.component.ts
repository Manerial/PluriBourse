import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatSortModule, Sort } from '@angular/material/sort';
import { TranslatePipe } from '@ngx-translate/core';
import { Subject, debounceTime, firstValueFrom } from 'rxjs';
import { EditionCategoryDto } from '../../../models/category.model';
import { EditionDto } from '../../../models/edition.model';
import { ArchivedCatalogFilter, ArchivedItemDto } from '../../../models/archived-item.model';
import { ArchivedItemService } from '../../../services/archived-item.service';
import { CategoryService } from '../../../services/category.service';
import { EditionService } from '../../../services/edition.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';

const DEFAULT_PAGE_SIZE = 50;
const TEXT_FILTER_DEBOUNCE_MS = 300;

type TriState = boolean | null;

@Component({
  selector: 'app-archived-catalog',
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
  templateUrl: './archived-catalog.component.html',
  styleUrl: './archived-catalog.component.scss',
})
export class ArchivedCatalogComponent implements OnInit {
  private readonly archivedItemService = inject(ArchivedItemService);
  private readonly categoryService = inject(CategoryService);
  private readonly editionService = inject(EditionService);
  private readonly destroyRef = inject(DestroyRef);

  readonly archivedEditions = signal<EditionDto[]>([]);
  readonly isLoadingEditions = signal(false);
  readonly selectedEditionId = signal<number | null>(null);
  readonly currency = computed(() => this.archivedEditions().find((e) => e.id === this.selectedEditionId())?.currency);

  readonly items = signal<ArchivedItemDto[]>([]);
  readonly totalElements = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = DEFAULT_PAGE_SIZE;
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);

  readonly categories = signal<EditionCategoryDto[]>([]);
  readonly categoryError = signal<string | null>(null);

  readonly nameFilter = signal('');
  readonly categoryNameFilter = signal<string | null>(null);
  readonly soldFilter = signal<TriState>(null);

  readonly sortField = signal<string | undefined>(undefined);
  readonly sortDirection = signal<'asc' | 'desc' | ''>('');

  private readonly textFilterChanged = new Subject<void>();
  private requestSequence = 0;
  private categoryRequestSequence = 0;

  constructor() {
    this.textFilterChanged
      .pipe(debounceTime(TEXT_FILTER_DEBOUNCE_MS), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        void this.loadPage(0);
      });
  }

  async ngOnInit(): Promise<void> {
    this.isLoadingEditions.set(true);
    try {
      const editions = await firstValueFrom(this.editionService.getAll());
      this.archivedEditions.set(
        editions.filter((edition) => edition.archived).sort((a, b) => b.startDate.localeCompare(a.startDate))
      );
    } catch {
      this.archivedEditions.set([]);
      this.error.set('admin.archivedCatalog.error.load');
    } finally {
      this.isLoadingEditions.set(false);
    }
  }

  async onEditionChange(editionId: number | null): Promise<void> {
    this.selectedEditionId.set(editionId);
    this.error.set(null);
    this.categoryError.set(null);
    this.categoryNameFilter.set(null);
    this.nameFilter.set('');
    this.soldFilter.set(null);
    this.sortField.set(undefined);
    this.sortDirection.set('');
    this.items.set([]);
    this.totalElements.set(0);
    this.categories.set([]);

    if (editionId === null) {
      return;
    }

    await this.loadCategories(editionId);
    await this.loadPage(0);
  }

  onNameInput(value: string): void {
    this.nameFilter.set(value);
    this.textFilterChanged.next();
  }

  async onCategoryChange(value: string | null): Promise<void> {
    this.categoryNameFilter.set(value);
    await this.loadPage(0);
  }

  async onSoldChange(value: TriState): Promise<void> {
    this.soldFilter.set(value);
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

  private async loadCategories(editionId: number): Promise<void> {
    const requestId = ++this.categoryRequestSequence;
    try {
      const categories = await firstValueFrom(this.categoryService.getCategories(editionId));
      if (requestId !== this.categoryRequestSequence) {
        return;
      }
      this.categories.set(categories);
      this.categoryError.set(null);
    } catch {
      if (requestId !== this.categoryRequestSequence) {
        return;
      }
      this.categories.set([]);
      this.categoryError.set('admin.archivedCatalog.error.load');
    }
  }

  private async loadPage(page: number): Promise<void> {
    const editionId = this.selectedEditionId();
    if (editionId === null) {
      return;
    }
    // Guards against out-of-order responses: rapid filter/sort/page changes can fire overlapping
    // requests, and network timing gives no guarantee the last one sent resolves last. Only the
    // response for the most recently issued request is applied; older ones are discarded.
    const requestId = ++this.requestSequence;
    this.isLoading.set(true);
    this.error.set(null);
    const filter: ArchivedCatalogFilter = {
      name: this.nameFilter().trim() || undefined,
      categoryName: this.categoryNameFilter() ?? undefined,
      sold: this.soldFilter() ?? undefined,
      page,
      size: this.pageSize,
      sort: this.buildSort(),
    };
    try {
      const result = await firstValueFrom(this.archivedItemService.getArchivedCatalog(editionId, filter));
      if (requestId !== this.requestSequence) {
        return;
      }
      this.items.set(result.page.content);
      this.totalElements.set(result.page.totalElements);
      this.pageIndex.set(page);
    } catch {
      if (requestId !== this.requestSequence) {
        return;
      }
      this.error.set('admin.archivedCatalog.error.load');
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
