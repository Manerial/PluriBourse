import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import Big from 'big.js';
import { EditionCategoryDto } from '../../../models/category.model';
import { CreateLotRequest, LotDto, UpdateLotRequest } from '../../../models/lot.model';
import { LotService } from '../../../services/lot.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../../shared/http-error.util';

interface LotItemRow {
  id: number | null;
  name: string;
  categoryId: number | null;
  incomplete: boolean;
  comment: string;
}

@Component({
  selector: 'app-lot-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
    NotificationInlineComponent,
  ],
  templateUrl: './lot-form.component.html',
  styleUrl: './lot-form.component.scss',
})
export class LotFormComponent {
  private readonly lotService = inject(LotService);
  private readonly fb = inject(FormBuilder);

  readonly sellerId = input.required<number>();
  readonly categories = input.required<EditionCategoryDto[]>();
  readonly editingLot = input<LotDto | null>(null);

  readonly saved = output<LotDto>();
  readonly cancelled = output<void>();

  readonly isEditing = computed(() => this.editingLot() !== null);

  readonly itemsFormArray = this.fb.array([this.createItemRow(), this.createItemRow()]);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    globalPrice: [0, [Validators.required, Validators.min(0.01)]],
    items: this.itemsFormArray,
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    effect(() => {
      const lot = this.editingLot();
      this.sellerId(); // re-run this effect on seller change too, even if editingLot() stays null
      this.error.set(null);
      if (lot) {
        this.form.patchValue({ name: lot.name, globalPrice: lot.globalPrice });
        this.setItemRows(
          lot.items.map(item => ({
            id: item.id,
            name: item.name,
            categoryId: item.categoryId,
            incomplete: item.incomplete,
            comment: item.comment ?? '',
          }))
        );
      } else {
        this.form.patchValue({ name: '', globalPrice: 0 });
        this.setItemRows([this.emptyItemRow(), this.emptyItemRow()]);
      }
    });
  }

  addItemRow(): void {
    this.itemsFormArray.push(this.createItemRow());
  }

  removeItemRow(index: number): void {
    if (this.itemsFormArray.length <= 2) {
      return;
    }
    this.itemsFormArray.removeAt(index);
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.itemsFormArray.length < 2) {
      return;
    }
    const raw = this.form.getRawValue();
    const editingLot = this.editingLot();

    this.error.set(null);
    this.loading.set(true);
    try {
      let result: LotDto;
      if (editingLot) {
        const dto: UpdateLotRequest = {
          name: raw.name.trim(),
          globalPrice: new Big(raw.globalPrice).round(2).toNumber(),
          items: raw.items.map(item => ({
            id: item.id,
            categoryId: item.categoryId!,
            name: item.name.trim(),
            incomplete: item.incomplete,
            comment: item.comment.trim() ? item.comment.trim() : null,
          })),
        };
        result = await firstValueFrom(this.lotService.update(editingLot.id, dto));
      } else {
        const dto: CreateLotRequest = {
          sellerProfileId: this.sellerId(),
          name: raw.name.trim(),
          globalPrice: new Big(raw.globalPrice).round(2).toNumber(),
          items: raw.items.map(item => ({
            categoryId: item.categoryId!,
            name: item.name.trim(),
            incomplete: item.incomplete,
            comment: item.comment.trim() ? item.comment.trim() : null,
          })),
        };
        result = await firstValueFrom(this.lotService.create(dto));
      }
      this.saved.emit(result);
      if (!editingLot) {
        this.form.patchValue({ name: '', globalPrice: 0 });
        this.setItemRows([this.emptyItemRow(), this.emptyItemRow()]);
      }
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/item-modification-locked')) {
        this.error.set('volunteer.deposit.item.lotForm.error.phaseLocked');
      } else if (err instanceof HttpErrorResponse && err.status === 404 && extractErrorType(err)?.endsWith('/no-active-edition')) {
        this.error.set('volunteer.deposit.item.lotForm.error.noActiveEdition');
      } else {
        this.error.set('volunteer.deposit.item.lotForm.error.save');
      }
    } finally {
      this.loading.set(false);
    }
  }

  cancel(): void {
    this.cancelled.emit();
  }

  private createItemRow(initial: LotItemRow = this.emptyItemRow()) {
    return this.fb.nonNullable.group({
      id: [initial.id as number | null],
      name: [initial.name, [Validators.required, Validators.maxLength(200)]],
      categoryId: [initial.categoryId, [Validators.required]],
      incomplete: [initial.incomplete],
      comment: [initial.comment, [Validators.maxLength(500)]],
    });
  }

  private emptyItemRow(): LotItemRow {
    return { id: null, name: '', categoryId: null, incomplete: false, comment: '' };
  }

  private setItemRows(rows: LotItemRow[]): void {
    this.itemsFormArray.clear();
    for (const row of rows) {
      this.itemsFormArray.push(this.createItemRow(row));
    }
  }
}
