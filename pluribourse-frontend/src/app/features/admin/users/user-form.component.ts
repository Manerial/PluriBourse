import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { UserService } from '../../../services/user.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, MatFormFieldModule, MatInputModule, NotificationInlineComponent],
  templateUrl: './user-form.component.html'
})
export class UserFormComponent {
  private readonly userService = inject(UserService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.maxLength(50)]],
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(128), Validators.pattern(/.*[A-Z].*/), Validators.pattern(/.*[0-9].*/)]]
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
      await firstValueFrom(this.userService.createVolunteer(this.form.getRawValue()));
      await this.router.navigate(['/admin/users']);
    } catch {
      this.error.set('admin.users.error.create');
    } finally {
      this.loading.set(false);
    }
  }

  async cancel(): Promise<void> {
    await this.router.navigate(['/admin/users']);
  }
}
