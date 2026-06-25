import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../services/auth.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { ToastService } from '../../../shared/components/toast/toast.service';

@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, MatFormFieldModule, MatInputModule, NotificationInlineComponent],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss'
})
export class ChangePasswordComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly translate = inject(TranslateService);
  private readonly toast = inject(ToastService);

  readonly form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*[A-Z].*/), Validators.pattern(/.*[0-9].*/)]]
  });

  readonly error = signal(false);
  readonly loading = signal(false);

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.error.set(false);
    this.loading.set(true);
    try {
      await this.auth.changePassword(this.form.getRawValue().newPassword);
      this.toast.showSuccess(this.translate.instant('auth.changePassword.success'));
      switch (this.auth.currentUser()?.role) {
        case 'ADMIN':
          await this.router.navigate(['/admin']);
          break;
        case 'VOLUNTEER':
          await this.router.navigate(['/volunteer']);
          break;
        default:
          await this.router.navigate(['/']);
          break;
      }
    } catch {
      this.error.set(true);
    } finally {
      this.loading.set(false);
    }
  }
}
