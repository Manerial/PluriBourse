import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { maxDecimalsValidator } from '../../../../shared/validators/financial.validators';

export interface UpdateCommissionRateDialogData {
  editionId: number;
  currentRate: number;
}

@Component({
  selector: 'app-update-commission-rate-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, A11yModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslatePipe],
  templateUrl: './update-commission-rate-dialog.component.html',
  styleUrl: './update-commission-rate-dialog.component.scss',
})
export class UpdateCommissionRateDialogComponent {
  readonly dialogRef = inject<DialogRef<number>>(DialogRef);
  readonly data = inject<UpdateCommissionRateDialogData>(DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    commissionRate: [this.data.currentRate, [Validators.required, Validators.min(0), Validators.max(100), maxDecimalsValidator(2)]]
  });

  confirm(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close(this.form.getRawValue().commissionRate);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
