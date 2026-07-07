import { Component, inject, input, output, signal } from '@angular/core';
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
import { EditionCategoryDto } from '../../../models/category.model';
import { CreateLotRequest, LotDto } from '../../../models/lot.model';
import { LotService } from '../../../services/lot.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../../shared/http-error.util';

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

  readonly saved = output<LotDto>();
  readonly cancelled = output<void>();

  readonly itemsFormArray = this.fb.array([this.createItemRow(), this.createItemRow()]);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(200)]],
    globalPrice: [0, [Validators.required, Validators.min(0.01)]],
    items: this.itemsFormArray,
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

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
    const dto: CreateLotRequest = {
      sellerProfileId: this.sellerId(),
      name: raw.name.trim(),
      globalPrice: Math.round(raw.globalPrice * 100) / 100,
      items: raw.items.map(item => ({
        categoryId: item.categoryId!,
        name: item.name.trim(),
        incomplete: item.incomplete,
        comment: item.comment.trim() ? item.comment.trim() : null,
      })),
    };

    this.error.set(null);
    this.loading.set(true);
    try {
      const result = await firstValueFrom(this.lotService.create(dto));
      this.saved.emit(result);
      this.resetForm();
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

  private createItemRow() {
    return this.fb.nonNullable.group({
      name: ['', [Validators.required, Validators.maxLength(200)]],
      categoryId: [null as number | null, [Validators.required]],
      incomplete: [false],
      comment: ['', [Validators.maxLength(500)]],
    });
  }

  private resetForm(): void {
    this.form.patchValue({ name: '', globalPrice: 0 });
    while (this.itemsFormArray.length > 0) {
      this.itemsFormArray.removeAt(0);
    }
    this.itemsFormArray.push(this.createItemRow());
    this.itemsFormArray.push(this.createItemRow());
  }
}
