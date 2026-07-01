import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';
import { Language } from '../../../models/language.enum';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { SkeletonRowComponent } from '../../../shared/components/skeleton-row/skeleton-row.component';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, TranslatePipe, SkeletonRowComponent, NotificationInlineComponent],
  templateUrl: './admin-settings.component.html'
})
export class AdminSettingsComponent implements OnInit {
  private readonly instanceConfigService = inject(GlobalInstanceConfigService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);

  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly loadError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    associationName: ['', [Validators.required, Validators.maxLength(255)]],
    defaultCommissionRate: [20, [Validators.required, Validators.min(0), Validators.max(100)]],
    defaultDocumentLanguage: [Language.EN, [Validators.required]]
  });

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);
    try {
      const config = await firstValueFrom(this.instanceConfigService.getConfig());
      this.form.patchValue(config);
    } catch {
      this.loadError.set('admin.settings.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.isSaving()) {
      return;
    }
    this.isSaving.set(true);
    try {
      const updated = await firstValueFrom(
        this.instanceConfigService.updateConfig(this.form.getRawValue())
      );
      this.form.patchValue(updated);
      this.toast.showSuccess(this.translate.instant('admin.settings.success'));
    } catch {
      this.toast.showError(this.translate.instant('admin.settings.error.save'));
    } finally {
      this.isSaving.set(false);
    }
  }
}
