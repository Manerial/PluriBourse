import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../services/auth.service';
import { AccountService } from '../../services/account.service';
import { Language } from '../../models/language.enum';

@Component({
  selector: 'app-account',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  templateUrl: './account.component.html'
})
export class AccountComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly translateService = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  readonly isSaving = signal(false);
  readonly saveSuccess = signal(false);
  readonly saveError = signal<string | null>(null);

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
    this.saveSuccess.set(false);
    this.saveError.set(null);
    this.isSaving.set(true);
    const lang = this.form.getRawValue().language;
    try {
      await firstValueFrom(this.accountService.updateLanguage(lang));
      this.translateService.use(lang.toLowerCase()).subscribe();
      const current = this.auth.currentUser();
      if (current) {
        this.auth.currentUser.set({ ...current, preferredLanguage: lang });
      }
      this.saveSuccess.set(true);
    } catch {
      this.saveError.set('account.error.save');
    } finally {
      this.isSaving.set(false);
    }
  }
}
