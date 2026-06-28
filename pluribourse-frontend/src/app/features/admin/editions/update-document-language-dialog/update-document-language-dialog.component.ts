import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe } from '@ngx-translate/core';

export interface UpdateDocumentLanguageDialogData {
  editionId: number;
  currentLanguage: 'EN' | 'FR';
}

@Component({
  selector: 'app-update-document-language-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, A11yModule, MatButtonModule, MatFormFieldModule, MatSelectModule, TranslatePipe],
  templateUrl: './update-document-language-dialog.component.html',
  styleUrl: './update-document-language-dialog.component.scss',
})
export class UpdateDocumentLanguageDialogComponent {
  readonly dialogRef = inject<DialogRef<'EN' | 'FR'>>(DialogRef);
  readonly data = inject<UpdateDocumentLanguageDialogData>(DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    documentLanguage: [this.data.currentLanguage as 'EN' | 'FR', [Validators.required]]
  });

  confirm(): void {
    if (this.form.invalid) {
      return;
    }
    this.dialogRef.close(this.form.getRawValue().documentLanguage);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
