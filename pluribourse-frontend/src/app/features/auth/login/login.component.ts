import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { AuthService } from '../../../services/auth.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../../shared/http-error.util';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslatePipe, NotificationInlineComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly translate = inject(TranslateService);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  readonly loading = signal(false);
  readonly error = signal<'invalid-credentials' | 'unauthorized-role' | 'account-disabled' | null>(null);
  readonly passwordVisible = signal(false);

  togglePasswordVisibility(): void {
    this.passwordVisible.update(visible => !visible);
  }

  passwordToggleLabel(): string {
    return this.translate.instant(this.passwordVisible() ? 'auth.login.passwordToggle.hide' : 'auth.login.passwordToggle.show');
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    const { username, password } = this.form.getRawValue();
    try {
      const user = await this.auth.login(username, password);
      // forcePasswordChange is handled declaratively by authGuard on the target route.
      switch (user.role) {
        case 'ADMIN':
          await this.router.navigate(['/admin']);
          break;
        case 'VOLUNTEER':
          await this.router.navigate(['/volunteer']);
          break;
        default:
          // SELLER and any future roles are blocked server-side; surface a generic error
          this.auth.clearSession();
          this.error.set('unauthorized-role');
      }
    } catch (err) {
      const errorType = err instanceof HttpErrorResponse ? extractErrorType(err) : undefined;
      if (errorType?.endsWith('/account-disabled')) {
        this.error.set('account-disabled');
      } else {
        this.error.set('invalid-credentials');
      }
    } finally {
      this.loading.set(false);
    }
  }
}
