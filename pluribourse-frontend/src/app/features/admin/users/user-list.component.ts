import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
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
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, TranslatePipe, SkeletonRowComponent, NotificationInlineComponent, EmptyStateComponent],
  templateUrl: './user-list.component.html',
  styleUrl: './user-list.component.scss'
})
export class UserListComponent implements OnInit {
  private readonly userService = inject(UserService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(Dialog);
  private readonly destroyRef = inject(DestroyRef);
  private readonly confirmDialog = inject(ConfirmDialogService);

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

  async confirmDelete(user: UserDto): Promise<void> {
    const confirmed = await firstValueFrom(
      this.confirmDialog.open({
        title: this.translate.instant('admin.users.deleteDialog.title'),
        description: this.translate.instant('admin.users.deleteDialog.description'),
        confirmVariant: 'error',
      })
    );
    if (!confirmed) {
      return;
    }
    this.submitting.set(true);
    try {
      await firstValueFrom(this.userService.deleteVolunteer(user.id));
      this.toast.showSuccess(this.translate.instant('admin.users.success.delete'));
      this.users.update(list => list.filter(u => u.id !== user.id));
    } catch {
      this.toast.showError(this.translate.instant('admin.users.error.delete'));
    } finally {
      this.submitting.set(false);
    }
  }

  navigateToCreate(): void {
    this.router.navigate(['create'], { relativeTo: this.route });
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
