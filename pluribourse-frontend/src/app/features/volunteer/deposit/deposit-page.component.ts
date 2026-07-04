import { Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { EditionCategoryDto } from '../../../models/category.model';
import { ItemDto } from '../../../models/item.model';
import { CategoryService } from '../../../services/category.service';
import { ItemService } from '../../../services/item.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { extractErrorType } from '../../../shared/http-error.util';
import { ItemFormComponent } from './item-form.component';
import { SellerSearchComponent } from './seller-search.component';

@Component({
  selector: 'app-deposit-page',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    TranslatePipe,
    EmptyStateComponent,
    NotificationInlineComponent,
    SkeletonRowComponent,
    ItemFormComponent,
    SellerSearchComponent,
  ],
  templateUrl: './deposit-page.component.html',
  styleUrl: './deposit-page.component.scss',
})
export class DepositPageComponent {
  private readonly categoryService = inject(CategoryService);
  private readonly itemService = inject(ItemService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  private readonly sellerSearchRef = viewChild(SellerSearchComponent);
  readonly selectedSeller = computed(() => this.sellerSearchRef()?.selectedSeller() ?? null);

  readonly categories = signal<EditionCategoryDto[]>([]);
  readonly items = signal<ItemDto[]>([]);
  readonly itemsLoading = signal(false);
  readonly itemsError = signal<string | null>(null);
  readonly editingItem = signal<ItemDto | null>(null);
  readonly commentEditId = signal<number | null>(null);
  readonly commentDraft = signal('');

  constructor() {
    this.loadCategories();

    effect(() => {
      const seller = this.selectedSeller();
      this.editingItem.set(null);
      this.commentEditId.set(null);
      if (seller) {
        this.loadItems(seller.id);
      } else {
        this.items.set([]);
      }
    });
  }

  startEdit(item: ItemDto): void {
    this.editingItem.set(item);
  }

  cancelEdit(): void {
    this.editingItem.set(null);
  }

  onItemSaved(): void {
    this.editingItem.set(null);
    const seller = this.selectedSeller();
    if (seller) {
      this.loadItems(seller.id);
    }
  }

  async toggleIncomplete(item: ItemDto): Promise<void> {
    try {
      const updated = await firstValueFrom(
        this.itemService.updateCompleteness(item.id, { incomplete: !item.incomplete, comment: item.comment })
      );
      this.items.update(items => items.map(i => (i.id === updated.id ? updated : i)));
    } catch {
      this.toast.showError(this.translate.instant('volunteer.deposit.item.error.update'));
    }
  }

  openCommentEditor(item: ItemDto): void {
    this.commentEditId.set(item.id);
    this.commentDraft.set(item.comment ?? '');
  }

  cancelCommentEdit(): void {
    this.commentEditId.set(null);
  }

  async saveComment(item: ItemDto): Promise<void> {
    const comment = this.commentDraft().trim() ? this.commentDraft().trim() : null;
    try {
      const updated = await firstValueFrom(
        this.itemService.updateCompleteness(item.id, { incomplete: item.incomplete, comment })
      );
      this.items.update(items => items.map(i => (i.id === updated.id ? updated : i)));
      this.commentEditId.set(null);
    } catch {
      this.toast.showError(this.translate.instant('volunteer.deposit.item.error.update'));
    }
  }

  async confirmDelete(item: ItemDto): Promise<void> {
    const confirmed = await firstValueFrom(
      this.confirmDialog.open({
        title: this.translate.instant('volunteer.deposit.item.deleteDialog.title'),
        description: this.translate.instant('volunteer.deposit.item.deleteDialog.description', { name: item.name }),
        confirmVariant: 'error',
      })
    );
    if (!confirmed) {
      return;
    }
    const seller = this.selectedSeller();
    try {
      await firstValueFrom(this.itemService.delete(item.id));
      this.toast.showSuccess(this.translate.instant('volunteer.deposit.item.success.delete'));
      if (seller) {
        await this.loadItems(seller.id);
      }
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/item-modification-locked')) {
        this.toast.showError(this.translate.instant('volunteer.deposit.item.error.phaseLocked'));
      } else {
        this.toast.showError(this.translate.instant('volunteer.deposit.item.error.delete'));
      }
    }
  }

  private async loadCategories(): Promise<void> {
    try {
      const categories = await firstValueFrom(this.categoryService.getCategoriesForActiveEdition());
      this.categories.set(categories);
    } catch {
      this.toast.showError(this.translate.instant('volunteer.deposit.item.error.loadCategories'));
    }
  }

  private async loadItems(sellerId: number): Promise<void> {
    this.itemsLoading.set(true);
    this.itemsError.set(null);
    try {
      const items = await firstValueFrom(this.itemService.getBySeller(sellerId));
      this.items.set(items);
    } catch {
      this.itemsError.set('volunteer.deposit.item.error.load');
    } finally {
      this.itemsLoading.set(false);
    }
  }
}
