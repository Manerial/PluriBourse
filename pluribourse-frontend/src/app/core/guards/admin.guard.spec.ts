import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService, CurrentUser } from '../../services/auth.service';
import { adminGuard } from './admin.guard';

describe('adminGuard', () => {
  let authService: AuthService;
  let router: Router;

  const adminUser: CurrentUser = { username: 'Admin', role: 'ADMIN', forcePasswordChange: false };
  const volunteerUser: CurrentUser = { username: 'vol1', role: 'VOLUNTEER', forcePasswordChange: false };

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

  const runGuard = () => TestBed.runInInjectionContext(() => adminGuard({} as any, {} as any));

  it('returns true for ADMIN', () => {
    authService.currentUser.set(adminUser);
    expect(runGuard()).toBe(true);
  });

  it('redirects authenticated VOLUNTEER to /volunteer, not /login', () => {
    authService.currentUser.set(volunteerUser);
    const result = runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/volunteer');
  });

  it('redirects unauthenticated user to /login', () => {
    authService.currentUser.set(null);
    const result = runGuard();
    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/login');
  });
});
