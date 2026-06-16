import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService, CurrentUser } from '../../services/auth.service';
import { authGuard } from './auth.guard';

describe('authGuard', () => {
  let authService: AuthService;
  let router: Router;

  const adminUser: CurrentUser = { username: 'Admin', role: 'ADMIN', forcePasswordChange: false };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting()
      ]
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  const runGuard = () => TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

  it('returns true when authenticated and forcePasswordChange is false', () => {
    authService.currentUser.set(adminUser);
    expect(runGuard()).toBe(true);
  });

  it('redirects to /login when not authenticated', () => {
    authService.currentUser.set(null);
    const result = runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });

  it('redirects to /change-password when forcePasswordChange is true', () => {
    authService.currentUser.set({ ...adminUser, forcePasswordChange: true });
    const result = runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/change-password');
  });

  it('returns true for VOLUNTEER without forcePasswordChange', () => {
    authService.currentUser.set({ username: 'vol1', role: 'VOLUNTEER', forcePasswordChange: false });
    expect(runGuard()).toBe(true);
  });
});
