import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import { EditionService } from '../../../services/edition.service';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { maxDecimalsValidator } from '../../../shared/validators/financial.validators';

@Component({
  selector: 'app-edition-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, RouterLink,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslatePipe, NotificationInlineComponent
  ],
  templateUrl: './edition-form.component.html',
})
export class EditionFormComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly instanceConfigService = inject(GlobalInstanceConfigService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  readonly isEditMode = signal(false);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly formError = signal<string | null>(null);

  private editionId: number | null = null;

  readonly titleKey = computed(() =>
    this.isEditMode() ? 'edition.edit.title' : 'edition.create.title'
  );
  readonly submitKey = computed(() =>
    this.isEditMode() ? 'edition.edit.submit' : 'edition.create.submit'
  );
  readonly cancelKey = computed(() =>
    this.isEditMode() ? 'edition.edit.cancel' : 'edition.create.cancel'
  );

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    commissionRate: [0, [Validators.required, Validators.min(0), Validators.max(100), maxDecimalsValidator(2)]],
    documentLanguage: ['EN' as 'EN' | 'FR', [Validators.required]]
  });

  async ngOnInit(): Promise<void> {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam !== null) {
      this.isEditMode.set(true);
      this.editionId = +idParam;
      await this.loadEdition(this.editionId);
    } else {
      await this.loadDefaults();
    }
  }

  private async loadEdition(id: number): Promise<void> {
    this.isLoading.set(true);
    try {
      const edition = await firstValueFrom(this.editionService.getById(id));
      this.form.patchValue({
        name: edition.name,
        commissionRate: edition.commissionRate,
        documentLanguage: edition.documentLanguage
      });
      if (edition.phase !== 'PREPARATION') {
        this.form.controls.commissionRate.disable();
      }
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
      const { name, commissionRate, documentLanguage } = this.form.getRawValue();
      if (this.isEditMode() && this.editionId !== null) {
        await firstValueFrom(
          this.editionService.update(this.editionId, { name, commissionRate, documentLanguage })
        );
        this.toast.showSuccess(this.translate.instant('edition.edit.success'));
      } else {
        await firstValueFrom(
          this.editionService.create({ name, commissionRate, documentLanguage })
        );
        this.toast.showSuccess(this.translate.instant('edition.create.success'));
      }
      this.router.navigateByUrl('/admin/editions');
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422) {
        const errorType: string = (err.error as { type?: string })?.type ?? '';
        if (errorType.endsWith('/commission-rate-frozen')) {
          this.formError.set('edition.edit.error.commissionRateFrozen');
        } else if (errorType.endsWith('/edition-already-active')) {
          this.formError.set('edition.create.error.alreadyActive');
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
}
