import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { AuthService, CurrentUser } from '../../services/auth.service';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  const adminUser: CurrentUser = { username: 'Admin', role: 'ADMIN', forcePasswordChange: false };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpMock.verify());

  it('clears currentUser and navigates to /login on 401', () => {
    authService.currentUser.set(adminUser);
    http.get('/test').subscribe({ error: () => {} });
    httpMock.expectOne('/test').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(authService.currentUser()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('navigates to /change-password on 403 password-change-required', () => {
    authService.currentUser.set(adminUser);
    http.get('/test').subscribe({ error: () => {} });
    httpMock.expectOne('/test').flush(
      { type: 'https://pluribourse/errors/password-change-required' },
      { status: 403, statusText: 'Forbidden' }
    );
    expect(router.navigate).toHaveBeenCalledWith(['/change-password']);
    expect(authService.currentUser()).toEqual(adminUser);
  });

  it('clears currentUser and navigates to /login on generic 403', () => {
    authService.currentUser.set(adminUser);
    http.get('/test').subscribe({ error: () => {} });
    httpMock.expectOne('/test').flush(
      { type: 'https://pluribourse/errors/access-denied' },
      { status: 403, statusText: 'Forbidden' }
    );
    expect(authService.currentUser()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('propagates the error to the caller after handling', async () => {
    let receivedStatus: number | undefined;
    http.get('/test').subscribe({
      error: (err) => { receivedStatus = err.status; }
    });
    httpMock.expectOne('/test').flush(null, { status: 401, statusText: 'Unauthorized' });
    expect(receivedStatus).toBe(401);
  });

  it('does not interfere with successful responses', async () => {
    let result: { ok: boolean } | undefined;
    http.get<{ ok: boolean }>('/test').subscribe({ next: (res) => { result = res; } });
    httpMock.expectOne('/test').flush({ ok: true });
    expect(result?.ok).toBe(true);
  });
});
