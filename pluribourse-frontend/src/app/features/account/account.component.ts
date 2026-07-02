import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { AccountService } from '../../services/account.service';
import { Language } from '../../models/language.enum';
import { ToastService } from '../../shared/components/toast/toast.service';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatSelectModule, TranslatePipe],
  templateUrl: './account.component.html'
})
export class AccountComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly translateService = inject(TranslateService);
  private readonly fb = inject(FormBuilder);
  private readonly toast = inject(ToastService);

  readonly isSaving = signal(false);

  readonly form = this.fb.nonNullable.group({
    language: [Language.EN, [Validators.required]]
  });

  ngOnInit(): void {
    this.form.patchValue({ language: this.auth.currentUser()?.preferredLanguage ?? Language.EN });
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isSaving()) {
      return;
    }
    this.isSaving.set(true);
    const lang = this.form.getRawValue().language;
    try {
      await firstValueFrom(this.accountService.updateLanguage(lang));
      this.auth.setPreferredLanguage(lang);
      try {
        await firstValueFrom(this.translateService.use(lang.toLowerCase()));
      } catch {
        // language switch failed locally; preference already saved server-side
      }
      this.toast.showSuccess(this.translateService.instant('account.success'));
    } catch {
      this.toast.showError(this.translateService.instant('account.error.save'));
    } finally {
      this.isSaving.set(false);
    }
  }
}
