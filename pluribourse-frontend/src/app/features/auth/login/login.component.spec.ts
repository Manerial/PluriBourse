import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { AuthService } from '../../../services/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  const mockAuth = {
    login: vi.fn(),
    currentUser: signal(null),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: mockAuth },
      ],
    }).compileComponents();
  });

  it('renders username and password fields', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement;
    expect(el.querySelector('input[autocomplete="username"]')).not.toBeNull();
    expect(el.querySelector('input[autocomplete="current-password"]')).not.toBeNull();
  });

  it('submit button is disabled when form is empty', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.disabled).toBe(true);
  });

  it('shows error alert when error signal is set', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.error.set('invalid-credentials');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('submit button uses mat-flat-button with primary color', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-flat-button][color="primary"]');
    expect(btn).not.toBeNull();
  });

  it('sets error to no-active-edition when backend returns that error type', async () => {
    mockAuth.login.mockRejectedValueOnce(
      new HttpErrorResponse({ error: { type: 'https://pluribourse/errors/no-active-edition' } })
    );
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ username: 'volunteer1', password: 'Admin' });
    await fixture.componentInstance.onSubmit();
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toBe('no-active-edition');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
