import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { UserDto } from '../../../models/user.model';
import { UserService } from '../../../services/user.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, RouterLink],
  templateUrl: './user-list.component.html'
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);

  readonly users = signal<UserDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly resetPasswordFor = signal<number | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly submitting = signal(false);

  readonly resetPasswordForm = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8)]]
  });

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      const volunteers = await firstValueFrom(this.userService.getVolunteers());
      this.users.set(volunteers);
    } catch {
      this.error.set('admin.users.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  async toggleEnabled(user: UserDto): Promise<void> {
    this.actionError.set(null);
    try {
      if (user.enabled) {
        await firstValueFrom(this.userService.disableVolunteer(user.id));
      } else {
        await firstValueFrom(this.userService.enableVolunteer(user.id));
      }
      this.users.update(list =>
        list.map(u => u.id === user.id ? { ...u, enabled: !u.enabled } : u)
      );
    } catch {
      this.actionError.set(user.enabled ? 'admin.users.error.disable' : 'admin.users.error.enable');
    }
  }

  showResetPassword(userId: number): void {
    this.resetPasswordFor.set(userId);
    this.resetPasswordForm.reset();
    this.actionError.set(null);
  }

  cancelResetPassword(): void {
    this.resetPasswordFor.set(null);
    this.resetPasswordForm.reset();
  }

  async submitResetPassword(userId: number): Promise<void> {
    if (this.resetPasswordForm.invalid || this.submitting()) {
      return;
    }
    this.actionError.set(null);
    this.submitting.set(true);
    try {
      const { newPassword } = this.resetPasswordForm.getRawValue();
      await firstValueFrom(this.userService.resetPassword(userId, newPassword));
      this.resetPasswordFor.set(null);
      this.resetPasswordForm.reset();
    } catch {
      this.actionError.set('admin.users.error.resetPassword');
    } finally {
      this.submitting.set(false);
    }
  }
}
