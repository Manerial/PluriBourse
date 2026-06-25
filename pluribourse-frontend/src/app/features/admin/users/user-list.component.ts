import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { UserDto } from '../../../models/user.model';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, RouterLink, SkeletonRowComponent, NotificationInlineComponent, EmptyStateComponent],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss'
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);

  readonly users = signal<UserDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly resetPasswordFor = signal<number | null>(null);
  readonly submitting = signal(false);

  readonly resetPasswordForm = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*[A-Z].*/), Validators.pattern(/.*[0-9].*/)]]
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
    const action = user.enabled ? 'disable' : 'enable';
    try {
      if (user.enabled) {
        await firstValueFrom(this.userService.disableVolunteer(user.id));
      } else {
        await firstValueFrom(this.userService.enableVolunteer(user.id));
      }
      this.toast.showSuccess(this.translate.instant(`admin.users.success.${action}`));
      this.users.update(list =>
        list.map(u => u.id === user.id ? { ...u, enabled: !u.enabled } : u)
      );
    } catch {
      this.toast.showError(this.translate.instant(`admin.users.error.${action}`));
    }
  }

  navigateToCreate(): void {
    this.router.navigateByUrl('/admin/users/create');
  }

  showResetPassword(userId: number): void {
    this.resetPasswordFor.set(userId);
    this.resetPasswordForm.reset();
  }

  cancelResetPassword(): void {
    this.resetPasswordFor.set(null);
    this.resetPasswordForm.reset();
  }

  async submitResetPassword(userId: number): Promise<void> {
    if (this.resetPasswordForm.invalid || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    try {
      const { newPassword } = this.resetPasswordForm.getRawValue();
      await firstValueFrom(this.userService.resetPassword(userId, newPassword));
      this.resetPasswordFor.set(null);
      this.resetPasswordForm.reset();
      this.toast.showSuccess(this.translate.instant('admin.users.success.resetPassword'));
    } catch {
      this.toast.showError(this.translate.instant('admin.users.error.resetPassword'));
    } finally {
      this.submitting.set(false);
    }
  }
}
