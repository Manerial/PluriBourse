import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../../services/auth.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslatePipe, NotificationInlineComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  readonly loading = signal(false);
  readonly error = signal<'invalid-credentials' | 'unauthorized-role' | 'account-disabled' | 'no-active-edition' | null>(null);

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    const { username, password } = this.form.getRawValue();
    try {
      const user = await this.auth.login(username, password);
      if (user.forcePasswordChange) {
        await this.router.navigate(['/change-password']);
        return;
      }
      switch (user.role) {
        case 'ADMIN':
          await this.router.navigate(['/admin']);
          break;
        case 'VOLUNTEER':
          await this.router.navigate(['/volunteer']);
          break;
        default:
          // SELLER and any future roles are blocked server-side; surface a generic error
          this.auth.currentUser.set(null);
          this.error.set('unauthorized-role');
      }
    } catch (err: any) {
      if (err?.error?.type === 'https://pluribourse/errors/no-active-edition') {
        this.error.set('no-active-edition');
      } else if (err?.error?.type === 'https://pluribourse/errors/account-disabled') {
        this.error.set('account-disabled');
      } else {
        this.error.set('invalid-credentials');
      }
    } finally {
      this.loading.set(false);
    }
  }
}
