import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { AuthService } from '../../../services/auth.service';
import { ChangePasswordComponent } from './change-password.component';
import { ToastService } from '../../../shared/components/toast/toast.service';

describe('ChangePasswordComponent', () => {
  const mockAuth = {
    changePassword: vi.fn(),
    currentUser: signal(null),
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  let fixture: ReturnType<typeof TestBed.createComponent<ChangePasswordComponent>>;
  let component: ChangePasswordComponent;

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: mockAuth },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ChangePasswordComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('renders newPassword field', () => {
    expect(fixture.nativeElement.querySelector('input[autocomplete="new-password"]')).not.toBeNull();
  });

  it('submit button is disabled when form is empty', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.disabled).toBe(true);
  });

  it('shows error alert when error signal is true', () => {
    component.error.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('submit button uses mat-flat-button with primary color', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-flat-button][color="primary"]');
    expect(btn).not.toBeNull();
  });

  it('disables submit when passwords do not match', () => {
    component.form.controls.newPassword.setValue('Password1');
    component.form.controls.confirmPassword.setValue('Password2');
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.confirmPassword.hasError('passwordsMismatch')).toBe(true);
  });

  it('enables submit when passwords match and meet requirements', () => {
    component.form.controls.newPassword.setValue('Password1');
    component.form.controls.confirmPassword.setValue('Password1');
    expect(component.form.valid).toBe(true);
  });

  it('keeps submit disabled when confirmPassword is empty', () => {
    component.form.controls.newPassword.setValue('Password1');
    component.form.controls.confirmPassword.setValue('');
    expect(component.form.invalid).toBe(true);
  });
});
