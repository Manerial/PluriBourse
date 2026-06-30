import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { AccountComponent } from './account.component';
import { AccountService } from '../../services/account.service';
import { AuthService, CurrentUser } from '../../services/auth.service';
import { Language } from '../../models/language.enum';
import { ToastService } from '../../shared/components/toast/toast.service';

const MOCK_USER: CurrentUser = {
  username: 'test',
  role: 'VOLUNTEER',
  forcePasswordChange: false,
  preferredLanguage: Language.FR
};

describe('AccountComponent', () => {
  let fixture: ComponentFixture<AccountComponent>;
  let component: AccountComponent;

  const currentUserSignal = signal<CurrentUser | null>(MOCK_USER);

  const accountServiceMock = {
    updateLanguage: vi.fn().mockReturnValue(of(undefined))
  };

  const authServiceMock = {
    currentUser: currentUserSignal,
    setPreferredLanguage: vi.fn((lang: Language) => {
      const current = currentUserSignal();
      if (current) {
        currentUserSignal.set({ ...current, preferredLanguage: lang });
      }
    })
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    accountServiceMock.updateLanguage.mockReturnValue(of(undefined));
    currentUserSignal.set(MOCK_USER);

    await TestBed.configureTestingModule({
      imports: [AccountComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: AccountService, useValue: accountServiceMock },
        { provide: AuthService, useValue: authServiceMock },
        { provide: ToastService, useValue: toastMock },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AccountComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('patches form with current preferredLanguage on init', () => {
    expect(component.form.getRawValue().language).toBe(Language.FR);
  });

  it('calls updateLanguage with selected value on submit', async () => {
    component.form.setValue({ language: Language.EN });
    await component.onSubmit();
    expect(accountServiceMock.updateLanguage).toHaveBeenCalledWith(Language.EN);
    expect(currentUserSignal()?.preferredLanguage).toBe(Language.EN);
  });

  it('shows success toast on successful submit', async () => {
    await component.onSubmit();
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(toastMock.showError).not.toHaveBeenCalled();
  });

  it('shows error toast on failed submit', async () => {
    accountServiceMock.updateLanguage.mockReturnValue(throwError(() => new Error('server error')));
    await component.onSubmit();
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
  });

  it('does not call updateLanguage when form is invalid', async () => {
    component.form.controls.language.setValue('' as any);
    component.form.controls.language.setErrors({ required: true });
    await component.onSubmit();
    expect(accountServiceMock.updateLanguage).not.toHaveBeenCalled();
  });
});
