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
  });

  it('renders newPassword field', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[autocomplete="new-password"]')).not.toBeNull();
  });

  it('submit button is disabled when form is empty', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.disabled).toBe(true);
  });

  it('shows error alert when error signal is true', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    fixture.componentInstance.error.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('submit button has btn-primary class', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.classList).toContain('btn-primary');
  });
});
