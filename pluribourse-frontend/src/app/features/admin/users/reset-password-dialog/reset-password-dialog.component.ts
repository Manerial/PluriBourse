import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { passwordStrengthValidators } from '../../../../shared/validators/password-strength.validator';
import { DialogShellComponent } from '../../../../shared/components/dialog-shell/dialog-shell.component';

export interface ResetPasswordDialogData {
  userName: string;
}

@Component({
  selector: 'app-reset-password-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    A11yModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslatePipe,
    DialogShellComponent,
  ],
  templateUrl: './reset-password-dialog.component.html',
})
export class ResetPasswordDialogComponent {
  readonly dialogRef = inject<DialogRef<string>>(DialogRef);
  readonly data = inject<ResetPasswordDialogData>(DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, ...passwordStrengthValidators]],
  });

  confirm(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close(this.form.getRawValue().newPassword);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
