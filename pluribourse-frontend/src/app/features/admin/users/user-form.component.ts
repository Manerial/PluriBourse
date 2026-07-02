import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { UserService } from '../../../services/user.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { DialogShellComponent } from '../../../shared/components/dialog-shell/dialog-shell.component';
import { passwordStrengthValidators } from '../../../shared/validators/password-strength.validator';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslatePipe, NotificationInlineComponent, DialogShellComponent],
  templateUrl: './user-form.component.html'
})
export class UserFormComponent {
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  readonly dialogRef = inject<DialogRef<void>>(DialogRef);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.maxLength(50)]],
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(128), ...passwordStrengthValidators]],
    role: ['VOLUNTEER' as const],
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
      this.dialogRef.close();
    } catch {
      this.error.set('admin.users.error.create');
    } finally {
      this.loading.set(false);
    }
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
