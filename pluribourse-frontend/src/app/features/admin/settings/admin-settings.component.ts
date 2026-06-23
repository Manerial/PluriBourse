import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './admin-settings.component.html'
})
export class AdminSettingsComponent implements OnInit {
  private readonly instanceConfigService = inject(GlobalInstanceConfigService);
  private readonly fb = inject(FormBuilder);

  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly saveSuccess = signal(false);
  readonly saveError = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    associationName: ['', [Validators.required, Validators.maxLength(255)]],
    defaultCommissionRate: [20, [Validators.required, Validators.min(0), Validators.max(100)]],
    defaultDocumentLanguage: ['EN' as 'EN' | 'FR', [Validators.required]]
  });

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    this.loadError.set(null);
    this.saveSuccess.set(false);
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
    if (this.form.invalid || this.isSaving()) return;
    this.saveSuccess.set(false);
    this.saveError.set(null);
    this.isSaving.set(true);
    try {
      const updated = await firstValueFrom(
        this.instanceConfigService.updateConfig(this.form.getRawValue())
      );
      this.form.patchValue(updated);
      this.saveSuccess.set(true);
    } catch {
      this.saveError.set('admin.settings.error.save');
    } finally {
      this.isSaving.set(false);
    }
  }
}
