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
import { LotDto } from '../../../models/lot.model';
import { CategoryService } from '../../../services/category.service';
import { CurrentEditionService } from '../../../services/current-edition.service';
import { DepositService } from '../../../services/deposit.service';
import { ItemService } from '../../../services/item.service';
import { LotService } from '../../../services/lot.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { extractErrorType } from '../../../shared/http-error.util';
import { ItemFormComponent } from './item-form.component';
import { LotFormComponent } from './lot-form.component';
import { SellerSearchComponent } from './seller-search.component';

type DepositMode = 'individual' | 'lot';

interface StandaloneItemRow {
  readonly kind: 'item';
  readonly item: ItemDto;
}

interface LotGroupRow {
  readonly kind: 'lot';
  readonly lotId: number;
  readonly lotName: string;
  readonly lotPrice: number;
  readonly members: ItemDto[];
}

type DepositListRow = StandaloneItemRow | LotGroupRow;

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
    LotFormComponent,
    SellerSearchComponent,
  ],
  templateUrl: './deposit-page.component.html',
  styleUrl: './deposit-page.component.scss',
})
export class DepositPageComponent {
  private readonly categoryService = inject(CategoryService);
  private readonly itemService = inject(ItemService);
  private readonly lotService = inject(LotService);
  private readonly depositService = inject(DepositService);
  private readonly currentEditionService = inject(CurrentEditionService);
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
  readonly editingLot = signal<LotDto | null>(null);
  readonly commentEditId = signal<number | null>(null);
  readonly commentDraft = signal('');
  readonly depositMode = signal<DepositMode>('individual');
  readonly reprintingSlip = signal(false);
  readonly reprintingLabels = signal(false);

  // The deposit slip's "reversement net attendu" is computed from what was deposited, not what
  // was actually sold — reprinting it stays Dépôt-only (backend: PhaseGuard.
  // requireDepositPhaseForSlipReprint), unlike the thermal labels, still reprintable in Post-vente.
  readonly isDepositPhase = computed(() => this.currentEditionService.currentEdition()?.phase === 'DEPOSIT');
  readonly currency = computed(() => this.currentEditionService.currentEdition()?.currency);

  /**
   * The deposited-items list shows one row per lot (not one row per member item, as the
   * underlying flat item list would otherwise produce) — members are nested inside their lot's row.
   */
  readonly displayRows = computed<DepositListRow[]>(() => {
    const rows: DepositListRow[] = [];
    const lotRows = new Map<number, LotGroupRow>();
    for (const item of this.items()) {
      if (item.lotId === null) {
        rows.push({ kind: 'item', item });
        continue;
      }
      let lotRow = lotRows.get(item.lotId);
      if (!lotRow) {
        lotRow = { kind: 'lot', lotId: item.lotId, lotName: item.lotName!, lotPrice: item.lotPrice!, members: [] };
        lotRows.set(item.lotId, lotRow);
        rows.push(lotRow);
      }
      lotRow.members.push(item);
    }
    return rows;
  });

  constructor() {
    this.loadCategories();

    effect(() => {
      const seller = this.selectedSeller();
      this.editingItem.set(null);
      this.editingLot.set(null);
      this.commentEditId.set(null);
      this.depositMode.set('individual');
      if (seller) {
        this.loadItems(seller.id);
      } else {
        this.items.set([]);
      }
    });
  }

  setDepositMode(mode: DepositMode): void {
    this.editingItem.set(null);
    this.editingLot.set(null);
    this.depositMode.set(mode);
  }

  startEdit(item: ItemDto): void {
    this.editingLot.set(null);
    this.depositMode.set('individual');
    this.editingItem.set(item);
  }

  cancelEdit(): void {
    this.editingItem.set(null);
  }

  startEditLot(lotId: number): void {
    const lotItems = this.items().filter(i => i.lotId === lotId);
    if (lotItems.length === 0) {
      return;
    }
    const [first] = lotItems;
    this.editingItem.set(null);
    this.depositMode.set('lot');
    this.editingLot.set({
      id: lotId,
      name: first.lotName!,
      globalPrice: first.lotPrice!,
      items: lotItems,
    });
  }

  async confirmDeleteLot(lotId: number, lotName: string): Promise<void> {
    const confirmed = await firstValueFrom(
      this.confirmDialog.open({
        title: this.translate.instant('volunteer.deposit.item.deleteLotDialog.title'),
        description: this.translate.instant('volunteer.deposit.item.deleteLotDialog.description', { name: lotName }),
        confirmVariant: 'error',
      })
    );
    if (!confirmed) {
      return;
    }
    const seller = this.selectedSeller();
    try {
      await firstValueFrom(this.lotService.delete(lotId));
      this.toast.showSuccess(this.translate.instant('volunteer.deposit.item.success.deleteLot'));
      if (this.editingLot()?.id === lotId) {
        this.editingLot.set(null);
      }
      if (seller) {
        await this.loadItems(seller.id);
      }
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/item-modification-locked')) {
        this.toast.showError(this.translate.instant('volunteer.deposit.item.error.deleteLotPhaseLocked'));
      } else {
        this.toast.showError(this.translate.instant('volunteer.deposit.item.error.deleteLot'));
      }
    }
  }

  onItemSaved(): void {
    this.editingItem.set(null);
    const seller = this.selectedSeller();
    if (seller) {
      this.loadItems(seller.id);
    }
  }

  onLotSaved(): void {
    this.depositMode.set('individual');
    this.editingLot.set(null);
    const seller = this.selectedSeller();
    if (seller) {
      this.loadItems(seller.id);
    }
  }

  async reprintLabels(): Promise<void> {
    const seller = this.selectedSeller();
    // Locked immediately on click, before the confirm dialog even opens — and blocked while the
    // other print action is in flight — so a rapid double-click or a click on both buttons in a
    // row can never stack two confirm dialogs or fire two overlapping print requests for the
    // same seller (each print job costs a physical sheet/roll that can't be recalled).
    if (!seller || this.reprintingLabels() || this.reprintingSlip()) {
      return;
    }
    this.reprintingLabels.set(true);
    try {
      const confirmed = await firstValueFrom(
        this.confirmDialog.open({
          title: this.translate.instant('volunteer.deposit.reprintLabelsDialog.title'),
          description: this.translate.instant('volunteer.deposit.reprintLabelsDialog.description'),
        })
      );
      if (!confirmed) {
        return;
      }
      await firstValueFrom(this.depositService.reprintLabels(seller.id));
      this.toast.showSuccess(this.translate.instant('volunteer.deposit.success.reprintLabels'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('volunteer.deposit.error.thermalPrinterUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('volunteer.deposit.error.reprintLabels'));
      }
    } finally {
      this.reprintingLabels.set(false);
    }
  }

  async reprintDepositSlip(): Promise<void> {
    const seller = this.selectedSeller();
    if (!seller || this.reprintingLabels() || this.reprintingSlip()) {
      return;
    }
    this.reprintingSlip.set(true);
    try {
      const confirmed = await firstValueFrom(
        this.confirmDialog.open({
          title: this.translate.instant('volunteer.deposit.reprintDialog.title'),
          description: this.translate.instant('volunteer.deposit.reprintDialog.description'),
        })
      );
      if (!confirmed) {
        return;
      }
      await firstValueFrom(this.depositService.reprintDepositSlip(seller.id));
      this.toast.showSuccess(this.translate.instant('volunteer.deposit.success.reprintSlip'));
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/invalid-printer-selection')) {
        this.toast.showError(this.translate.instant('volunteer.deposit.error.a4PrinterUnavailable'));
      } else {
        this.toast.showError(this.translate.instant('volunteer.deposit.error.reprintSlip'));
      }
    } finally {
      this.reprintingSlip.set(false);
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
