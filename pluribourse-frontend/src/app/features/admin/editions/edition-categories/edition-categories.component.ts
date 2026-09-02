import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
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
import { DialogShellComponent } from '../../../../shared/components/dialog-shell/dialog-shell.component';

interface EditableCategoryRow {
  id: number | null;
  name: string;
  tableInput: string;
  nameTouched: boolean;
  tableTouched: boolean;
}

export interface EditionCategoriesDialogData {
  editionId: number;
}

@Component({
  selector: 'app-edition-categories',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslatePipe, NotificationInlineComponent, SkeletonRowComponent, DialogShellComponent
  ],
  templateUrl: './edition-categories.component.html',
})
export class EditionCategoriesComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly categoryService = inject(CategoryService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly dialogRef = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<EditionCategoriesDialogData>(DIALOG_DATA);

  readonly edition = signal<EditionDto | null>(null);
  readonly closedEditions = signal<EditionDto[]>([]);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly isReadOnly = computed(() => this.edition()?.phase !== 'PREPARATION');
  readonly error = signal<string | null>(null);
  readonly selectedSourceEditionId = signal<number | null>(null);

  private readonly langChange = toSignal(this.translate.onLangChange, { initialValue: null });

  readonly lockedPhaseLabel = computed(() => {
    this.langChange();
    const phase = this.edition()?.phase;
    return phase ? this.translate.instant('edition.phase.' + phase) : '';
  });

  readonly categories = signal<EditableCategoryRow[]>([]);
  private readonly saveAttempted = signal(false);

  private editionId = 0;

  async ngOnInit(): Promise<void> {
    this.editionId = this.data.editionId;
    if (!this.editionId || this.editionId <= 0) {
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
      this.categories.set(cats.map(c => this.toRow(c)));
      this.closedEditions.set(allEditions.filter(e => e.phase === 'CLOSED' && e.id !== this.editionId));
    } catch {
      this.error.set('category.load.error');
    } finally {
      this.isLoading.set(false);
    }
  }

  addCategory(): void {
    this.categories.update(rows => [...rows, { id: null, name: '', tableInput: '', nameTouched: false, tableTouched: false }]);
  }

  removeCategory(index: number): void {
    this.categories.update(rows => rows.filter((_, i) => i !== index));
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
      const dtos: EditionCategoryDto[] = this.categories().map(row => ({
        id: row.id,
        name: row.name,
        tableNumbers: this.parseTableInput(row.tableInput),
      }));
      const saved = await firstValueFrom(this.categoryService.saveCategories(this.editionId, dtos));
      this.categories.set(saved.map(c => this.toRow(c)));
      this.toast.showSuccess(this.translate.instant('category.save.success'));
      this.dialogRef.close();
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
      this.categories.set(copied.map(c => this.toRow(c)));
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

  markNameTouched(row: EditableCategoryRow): void {
    row.nameTouched = true;
  }

  markTableTouched(row: EditableCategoryRow): void {
    row.tableTouched = true;
  }

  /**
   * A required-field error surfaces once the field has been visited (blur) or a save has been attempted,
   * and clears itself as soon as the field holds a valid value.
   */
  isNameInvalid(row: EditableCategoryRow): boolean {
    return !this.isReadOnly()
      && row.name.trim().length === 0
      && (row.nameTouched || this.saveAttempted());
  }

  isTableInvalid(row: EditableCategoryRow): boolean {
    return !this.isReadOnly()
      && this.parseTableInput(row.tableInput).length === 0
      && (row.tableTouched || this.saveAttempted());
  }

  nameErrorMatcher(row: EditableCategoryRow): ErrorStateMatcher {
    return { isErrorState: () => this.isNameInvalid(row) };
  }

  tableErrorMatcher(row: EditableCategoryRow): ErrorStateMatcher {
    return { isErrorState: () => this.isTableInvalid(row) };
  }

  private validateRows(): boolean {
    this.saveAttempted.set(true);
    return this.categories().every(row =>
      row.name.trim().length > 0 && this.parseTableInput(row.tableInput).length > 0
    );
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
      nameTouched: false,
      tableTouched: false,
    };
  }
}
