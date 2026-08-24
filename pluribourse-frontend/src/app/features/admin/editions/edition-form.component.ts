import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import { EditionService } from '../../../services/edition.service';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { DialogShellComponent } from '../../../shared/components/dialog-shell/dialog-shell.component';
import { maxDecimalsValidator } from '../../../shared/validators/financial.validators';
import { dateRangeValidator } from '../../../shared/validators/date-range.validator';

export interface EditionFormDialogData {
  editionId: number | null; // null = create mode
}

function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function fromIsoDate(isoDate: string): Date {
  const [year, month, day] = isoDate.split('-').map(Number);
  return new Date(year, month - 1, day);
}

@Component({
  selector: 'app-edition-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule, MatDatepickerModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslatePipe, NotificationInlineComponent, DialogShellComponent
  ],
  templateUrl: './edition-form.component.html',
})
export class EditionFormComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly instanceConfigService = inject(GlobalInstanceConfigService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  readonly dialogRef = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<EditionFormDialogData>(DIALOG_DATA);

  readonly isEditMode = computed(() => this.data.editionId !== null);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly loadedEditionName = signal<string | null>(null);

  private readonly langChange = toSignal(this.translate.onLangChange, { initialValue: null });

  readonly dialogTitle = computed(() => {
    this.langChange();
    return this.isEditMode()
      ? (this.loadedEditionName() ?? this.translate.instant('edition.edit.title'))
      : this.translate.instant('edition.create.title');
  });
  readonly submitKey = computed(() =>
    this.isEditMode() ? 'edition.edit.submit' : 'edition.create.submit'
  );
  readonly cancelKey = computed(() =>
    this.isEditMode() ? 'edition.edit.cancel' : 'edition.create.cancel'
  );

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    commissionRate: [0, [Validators.required, Validators.min(0), Validators.max(100), maxDecimalsValidator(2)]],
    documentLanguage: ['EN' as 'EN' | 'FR', [Validators.required]],
    startDate: [null as Date | null, [Validators.required]],
    endDate: [null as Date | null, [Validators.required]]
  }, { validators: [dateRangeValidator('startDate', 'endDate')] });

  async ngOnInit(): Promise<void> {
    if (this.data.editionId !== null) {
      await this.loadEdition(this.data.editionId);
    } else {
      await this.loadDefaults();
    }
  }

  private async loadEdition(id: number): Promise<void> {
    this.isLoading.set(true);
    try {
      const edition = await firstValueFrom(this.editionService.getById(id));
      this.loadedEditionName.set(edition.name);
      this.form.patchValue({
        name: edition.name,
        commissionRate: edition.commissionRate,
        documentLanguage: edition.documentLanguage,
        startDate: fromIsoDate(edition.startDate),
        endDate: fromIsoDate(edition.endDate)
      });
    } catch {
      this.formError.set('edition.edit.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  private async loadDefaults(): Promise<void> {
    this.isLoading.set(true);
    try {
      const config = await firstValueFrom(this.instanceConfigService.getConfig());
      this.form.patchValue({
        commissionRate: config.defaultCommissionRate,
        documentLanguage: config.defaultDocumentLanguage as 'EN' | 'FR'
      });
    } catch {
      // Non-critical: form defaults remain (0 / EN)
    } finally {
      this.isLoading.set(false);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isSaving()) {
      return;
    }
    this.isSaving.set(true);
    this.formError.set(null);
    try {
      const { name, commissionRate, documentLanguage, startDate, endDate } = this.form.getRawValue();
      const payload = {
        name,
        commissionRate,
        documentLanguage,
        startDate: toIsoDate(startDate!),
        endDate: toIsoDate(endDate!)
      };
      if (this.isEditMode() && this.data.editionId !== null) {
        await firstValueFrom(this.editionService.update(this.data.editionId, payload));
        this.toast.showSuccess(this.translate.instant('edition.edit.success'));
      } else {
        await firstValueFrom(this.editionService.create(payload));
        this.toast.showSuccess(this.translate.instant('edition.create.success'));
      }
      this.dialogRef.close();
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422) {
        const errorType: string = (err.error as { type?: string })?.type ?? '';
        if (errorType.endsWith('/edition-cannot-be-updated')) {
          this.formError.set('edition.edit.error.cannotUpdate');
        } else if (errorType.endsWith('/edition-already-active')) {
          this.formError.set('edition.create.error.alreadyActive');
        } else if (errorType.endsWith('/association-name-not-configured')) {
          this.formError.set('edition.create.error.associationNameNotConfigured');
        } else {
          const key422 = this.isEditMode() ? 'edition.edit.error.save' : 'edition.create.error.save';
          this.toast.showError(this.translate.instant(key422));
        }
      } else {
        const key = this.isEditMode() ? 'edition.edit.error.save' : 'edition.create.error.save';
        this.toast.showError(this.translate.instant(key));
      }
    } finally {
      this.isSaving.set(false);
    }
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
