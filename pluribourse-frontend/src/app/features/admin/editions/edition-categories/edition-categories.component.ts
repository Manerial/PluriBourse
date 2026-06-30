import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { ErrorStateMatcher } from '@angular/material/core';
import { firstValueFrom } from 'rxjs';
import { EditionDto } from '../../../../models/edition.model';
import { EditionCategoryDto } from '../../../../models/category.model';
import { EditionService } from '../../../../services/edition.service';
import { CategoryService } from '../../../../services/category.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { NotificationInlineComponent } from '../../../../shared/components/notification-inline/notification-inline.component';
import { SkeletonRowComponent } from '../../../../shared/components/skeleton-row/skeleton-row.component';

interface EditableCategoryRow {
  id: number | null;
  name: string;
  tableInput: string;
  tableError: string | null;
}

@Component({
  selector: 'app-edition-categories',
  standalone: true,
  imports: [
    FormsModule, RouterLink,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslatePipe, NotificationInlineComponent, SkeletonRowComponent
  ],
  templateUrl: './edition-categories.component.html',
})
export class EditionCategoriesComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly categoryService = inject(CategoryService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly route = inject(ActivatedRoute);

  readonly edition = signal<EditionDto | null>(null);
  readonly closedEditions = signal<EditionDto[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly isReadOnly = computed(() => this.edition()?.phase !== 'PREPARATION');
  readonly error = signal<string | null>(null);
  readonly selectedSourceEditionId = signal<number | null>(null);

  categories: EditableCategoryRow[] = [];

  private editionId = 0;

  async ngOnInit(): Promise<void> {
    const rawId = this.route.snapshot.paramMap.get('id');
    this.editionId = Number(rawId);
    if (!rawId || isNaN(this.editionId) || this.editionId <= 0) {
      this.error.set('category.load.error');
      return;
    }
    this.isLoading.set(true);
    this.error.set(null);
    try {
      const [ed, cats, allEditions] = await Promise.all([
        firstValueFrom(this.editionService.getById(this.editionId)),
        firstValueFrom(this.categoryService.getCategories(this.editionId)),
        firstValueFrom(this.editionService.getAll()),
      ]);
      this.edition.set(ed);
      this.categories = cats.map(c => this.toRow(c));
      this.closedEditions.set(allEditions.filter(e => e.phase === 'CLOSED' && e.id !== this.editionId));
    } catch {
      this.error.set('category.load.error');
    } finally {
      this.isLoading.set(false);
    }
  }

  addCategory(): void {
    this.categories = [...this.categories, { id: null, name: '', tableInput: '', tableError: null }];
  }

  removeCategory(index: number): void {
    this.categories = this.categories.filter((_, i) => i !== index);
  }

  async onSave(): Promise<void> {
    if (this.isSaving()) {
      return;
    }
    if (!this.validateRows()) {
      return;
    }
    this.isSaving.set(true);
    try {
      const dtos: EditionCategoryDto[] = this.categories.map(row => ({
        id: row.id,
        name: row.name,
        tableNumbers: this.parseTableInput(row.tableInput),
      }));
      const saved = await firstValueFrom(this.categoryService.saveCategories(this.editionId, dtos));
      this.categories = saved.map(c => this.toRow(c));
      this.toast.showSuccess(this.translate.instant('category.save.success'));
    } catch {
      this.toast.showError(this.translate.instant('category.save.error'));
    } finally {
      this.isSaving.set(false);
    }
  }

  async onCopy(): Promise<void> {
    const sourceId = this.selectedSourceEditionId();
    if (sourceId === null || this.isSaving()) {
      return;
    }
    this.isSaving.set(true);
    try {
      const copied = await firstValueFrom(this.categoryService.copyFromEdition(this.editionId, sourceId));
      this.categories = copied.map(c => this.toRow(c));
      this.toast.showSuccess(this.translate.instant('category.copy.success'));
    } catch {
      this.toast.showError(this.translate.instant('category.copy.error'));
    } finally {
      this.isSaving.set(false);
    }
  }

  onSelectSource(editionId: number): void {
    this.selectedSourceEditionId.set(editionId);
  }

  nameErrorMatcher(row: EditableCategoryRow): ErrorStateMatcher {
    return { isErrorState: () => row.tableError === 'category.row.error.nameRequired' };
  }

  tableErrorMatcher(row: EditableCategoryRow): ErrorStateMatcher {
    return { isErrorState: () => row.tableError === 'category.row.error.tableRequired' };
  }

  private validateRows(): boolean {
    let valid = true;
    this.categories = this.categories.map(row => {
      if (!row.name.trim()) {
        valid = false;
        return { ...row, tableError: 'category.row.error.nameRequired' };
      }
      if (this.parseTableInput(row.tableInput).length === 0) {
        valid = false;
        return { ...row, tableError: 'category.row.error.tableRequired' };
      }
      return { ...row, tableError: null };
    });
    return valid;
  }

  private parseTableInput(input: string): number[] {
    return input
      .split(',')
      .map(s => parseInt(s.trim(), 10))
      .filter(n => !isNaN(n) && n > 0);
  }

  private toRow(dto: EditionCategoryDto): EditableCategoryRow {
    return {
      id: dto.id,
      name: dto.name,
      tableInput: dto.tableNumbers.join(', '),
      tableError: null,
    };
  }
}
