import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { EditionCategoryDto } from '../../../models/category.model';
import { ItemDto } from '../../../models/item.model';
import { ItemService } from '../../../services/item.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../../shared/http-error.util';

@Component({
  selector: 'app-item-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    TranslatePipe,
    NotificationInlineComponent,
  ],
  templateUrl: './item-form.component.html',
  styleUrl: './item-form.component.scss',
})
export class ItemFormComponent {
  private readonly itemService = inject(ItemService);
  private readonly fb = inject(FormBuilder);

  readonly sellerId = input.required<number>();
  readonly categories = input.required<EditionCategoryDto[]>();
  readonly editingItem = input<ItemDto | null>(null);

  readonly saved = output<ItemDto>();
  readonly cancelled = output<void>();

  readonly isEditing = computed(() => this.editingItem() !== null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    price: [0, [Validators.required, Validators.min(0.01)]],
    categoryId: [null as number | null, [Validators.required]],
    incomplete: [false],
    comment: ['', [Validators.maxLength(500)]],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly assignedTableNumber = signal<number | null>(null);

  constructor() {
    effect(() => {
      const item = this.editingItem();
      this.sellerId(); // re-run this effect on seller change too, even if editingItem() stays null
      this.assignedTableNumber.set(null);
      this.error.set(null);
      if (item) {
        this.form.setValue({
          name: item.name,
          price: item.price ?? 0,
          categoryId: item.categoryId,
          incomplete: item.incomplete,
          comment: item.comment ?? '',
        });
      } else {
        this.form.reset({ name: '', price: 0, categoryId: null, incomplete: false, comment: '' });
      }
    });
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    const raw = this.form.getRawValue();
    const dto = {
      sellerProfileId: this.sellerId(),
      categoryId: raw.categoryId!,
      name: raw.name,
      price: raw.price,
      incomplete: raw.incomplete,
      comment: raw.comment.trim() ? raw.comment.trim() : null,
    };

    this.error.set(null);
    this.loading.set(true);
    try {
      const editing = this.editingItem();
      const result = editing
        ? await firstValueFrom(this.itemService.update(editing.id, dto))
        : await firstValueFrom(this.itemService.create(dto));
      this.assignedTableNumber.set(result.tableNumber);
      this.saved.emit(result);
      if (!editing) {
        this.form.reset({ name: '', price: 0, categoryId: null, incomplete: false, comment: '' });
      }
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422 && extractErrorType(err)?.endsWith('/item-modification-locked')) {
        this.error.set('volunteer.deposit.item.form.error.phaseLocked');
      } else if (err instanceof HttpErrorResponse && err.status === 404 && extractErrorType(err)?.endsWith('/no-active-edition')) {
        this.error.set('volunteer.deposit.item.form.error.noActiveEdition');
      } else {
        this.error.set('volunteer.deposit.item.form.error.save');
      }
    } finally {
      this.loading.set(false);
    }
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
