import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../services/auth.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './change-password.component.html'
})
export class ChangePasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  readonly error = signal(false);
  readonly loading = signal(false);

  async onSubmit(): Promise<void> {
    if (this.form.invalid) return;
    this.error.set(false);
    this.loading.set(true);
    try {
      await this.auth.changePassword(this.form.getRawValue().newPassword);
      switch (this.auth.currentUser()?.role) {
        case 'ADMIN':
          await this.router.navigate(['/admin']);
          break;
        case 'VOLUNTEER':
          await this.router.navigate(['/volunteer']);
          break;
      }
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
