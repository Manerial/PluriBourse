import { Component, inject, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { SellerDto } from '../../../models/seller.model';
import { SellerService } from '../../../services/seller.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';

@Component({
  selector: 'app-seller-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslatePipe, NotificationInlineComponent],
  templateUrl: './seller-form.component.html'
})
export class SellerFormComponent {
  private readonly sellerService = inject(SellerService);
  private readonly fb = inject(FormBuilder);

  readonly created = output<SellerDto>();
  readonly cancelled = output<void>();

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.required, Validators.maxLength(30)]],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    try {
      const seller = await firstValueFrom(this.sellerService.create(this.form.getRawValue()));
      this.created.emit(seller);
    } catch {
      this.error.set('volunteer.deposit.form.error.create');
    } finally {
      this.loading.set(false);
    }
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
