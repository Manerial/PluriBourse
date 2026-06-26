import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { Dialog } from '@angular/cdk/dialog';
import { UserDto } from '../../../models/user.model';
import { UserService } from '../../../services/user.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ResetPasswordDialogComponent, ResetPasswordDialogData } from './reset-password-dialog/reset-password-dialog.component';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [TranslatePipe, RouterLink, SkeletonRowComponent, NotificationInlineComponent, EmptyStateComponent],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss'
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly dialog = inject(Dialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly users = signal<UserDto[]>([]);
  readonly isLoading = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitting = signal(false);

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

  openResetPasswordDialog(user: UserDto): void {
    const ref = this.dialog.open<string, ResetPasswordDialogData, ResetPasswordDialogComponent>(
      ResetPasswordDialogComponent,
      {
        data: { userName: `${user.firstName} ${user.lastName}` },
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabelledBy: 'reset-dialog-title',
        ariaDescribedBy: 'reset-dialog-desc',
      }
    );
    ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(async (newPassword) => {
      if (newPassword === undefined) {
        return;
      }
      this.submitting.set(true);
      try {
        await firstValueFrom(this.userService.resetPassword(user.id, newPassword));
        this.toast.showSuccess(this.translate.instant('admin.users.success.resetPassword'));
      } catch {
        this.toast.showError(this.translate.instant('admin.users.error.resetPassword'));
      } finally {
        this.submitting.set(false);
      }
    });
  }
}
