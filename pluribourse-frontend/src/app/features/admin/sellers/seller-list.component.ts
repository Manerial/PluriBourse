import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { SellerDto } from '../../../models/seller.model';
import { SellerService } from '../../../services/seller.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

const DEFAULT_PAGE_SIZE = 50;

@Component({
  selector: 'app-seller-list',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, MatPaginatorModule, TranslatePipe, SkeletonRowComponent, NotificationInlineComponent, EmptyStateComponent],
  templateUrl: './seller-list.component.html',
  styleUrl: './seller-list.component.scss'
})
export class SellerListComponent implements OnInit {
  private readonly sellerService = inject(SellerService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly confirmDialog = inject(ConfirmDialogService);

  readonly sellers = signal<SellerDto[]>([]);
  readonly totalElements = signal(0);
  readonly pageIndex = signal(0);
  readonly pageSize = DEFAULT_PAGE_SIZE;
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

  async ngOnInit(): Promise<void> {
    await this.loadPage(0);
  }

  async onPageChange(event: PageEvent): Promise<void> {
    await this.loadPage(event.pageIndex);
  }

  private async loadPage(page: number): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      const result = await firstValueFrom(this.sellerService.getSellers(page, this.pageSize));
      this.sellers.set(result.content);
      this.totalElements.set(result.totalElements);
      this.pageIndex.set(page);
    } catch {
      this.error.set('admin.sellers.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  async confirmDelete(seller: SellerDto): Promise<void> {
    const confirmed = await firstValueFrom(
      this.confirmDialog.open({
        title: this.translate.instant('admin.sellers.deleteDialog.title'),
        description: this.translate.instant('admin.sellers.deleteDialog.description'),
        confirmVariant: 'error',
      })
    );
    if (!confirmed) {
      return;
    }
    this.submitting.set(true);
    try {
      await firstValueFrom(this.sellerService.delete(seller.id));
      this.toast.showSuccess(this.translate.instant('admin.sellers.success.delete'));
      const isLastRowOnPage = this.sellers().length === 1;
      const targetPage = isLastRowOnPage && this.pageIndex() > 0 ? this.pageIndex() - 1 : this.pageIndex();
      await this.loadPage(targetPage);
    } catch {
      this.toast.showError(this.translate.instant('admin.sellers.error.delete'));
    } finally {
      this.submitting.set(false);
    }
  }
}
