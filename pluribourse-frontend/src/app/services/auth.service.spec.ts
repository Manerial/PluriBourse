import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { AuthService, CurrentUser } from './auth.service';
import { Language } from '../models/language.enum';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let routerMock: { navigate: ReturnType<typeof vi.fn> };
  let translateServiceMock: { use: ReturnType<typeof vi.fn> };

  const adminUser: CurrentUser = { username: 'Admin', role: 'ADMIN', forcePasswordChange: false, preferredLanguage: Language.FR };

  beforeEach(() => {
    routerMock = { navigate: vi.fn().mockResolvedValue(true) };
    translateServiceMock = { use: vi.fn().mockReturnValue(of({})) };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: routerMock },
        { provide: TranslateService, useValue: translateServiceMock }
      ]
    });

    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  describe('login', () => {
    it('sets currentUser and returns user on success', async () => {
      const promise = service.login('Admin', 'Secret1');
      httpMock.expectOne('/api/auth/login').flush(adminUser);
      const result = await promise;
      expect(result).toEqual(adminUser);
      expect(service.currentUser()).toEqual(adminUser);
      expect(service.isAuthenticated()).toBe(true);
    });

    it('propagates error and leaves currentUser null on failure', async () => {
      const promise = service.login('Admin', 'wrong');
      httpMock.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
      expect(promise).rejects.toThrow();
      expect(service.currentUser()).toBeNull();
    });
  });

  describe('logout', () => {
    it('clears currentUser and navigates to /login on success', async () => {
      service.currentUser.set(adminUser);
      const promise = service.logout();
      httpMock.expectOne('/api/auth/logout').flush(null);
      await promise;
      expect(service.currentUser()).toBeNull();
      expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
    });

    it('still clears currentUser and navigates even when server call fails', async () => {
      service.currentUser.set(adminUser);
      const promise = service.logout().catch(() => {});
      httpMock.expectOne('/api/auth/logout').flush(null, { status: 500, statusText: 'Server Error' });
      await promise;
      expect(service.currentUser()).toBeNull();
      expect(routerMock.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('changePassword', () => {
    it('clears forcePasswordChange flag locally on success', async () => {
      service.currentUser.set({ ...adminUser, forcePasswordChange: true });
      const promise = service.changePassword('NewPass1');
      httpMock.expectOne('/api/auth/change-password').flush(null);
      await promise;
      expect(service.currentUser()?.forcePasswordChange).toBe(false);
    });

    it('preserves other user fields after password change', async () => {
      service.currentUser.set({ ...adminUser, forcePasswordChange: true });
      const promise = service.changePassword('NewPass1');
      httpMock.expectOne('/api/auth/change-password').flush(null);
      await promise;
      expect(service.currentUser()?.username).toBe('Admin');
      expect(service.currentUser()?.role).toBe('ADMIN');
    });
  });

  describe('restoreSession', () => {
    it('sets currentUser on 200', async () => {
      const promise = service.restoreSession();
      httpMock.expectOne('/api/auth/me').flush(adminUser);
      await promise;
      expect(service.currentUser()).toEqual(adminUser);
    });

    it('clears currentUser on 401', async () => {
      service.currentUser.set(adminUser);
      const promise = service.restoreSession();
      httpMock.expectOne('/api/auth/me').flush(null, { status: 401, statusText: 'Unauthorized' });
      await promise;
      expect(service.currentUser()).toBeNull();
    });

    it('keeps currentUser on 403 password-change-required to avoid redirect loop', async () => {
      service.currentUser.set(adminUser);
      const promise = service.restoreSession();
      httpMock.expectOne('/api/auth/me').flush(
        { type: 'https://pluribourse/errors/password-change-required' },
        { status: 403, statusText: 'Forbidden' }
      );
      await promise;
      expect(service.currentUser()).toEqual(adminUser);
    });

    it('clears currentUser on generic 403', async () => {
      service.currentUser.set(adminUser);
      const promise = service.restoreSession();
      httpMock.expectOne('/api/auth/me').flush(
        { type: 'https://pluribourse/errors/other' },
        { status: 403, statusText: 'Forbidden' }
      );
      await promise;
      expect(service.currentUser()).toBeNull();
    });
  });

  describe('language switching', () => {
    it('applies preferredLanguage from server on login', async () => {
      const promise = service.login('Admin', 'Admin');
      httpMock.expectOne('/api/auth/login').flush(adminUser);
      await promise;
      expect(translateServiceMock.use).toHaveBeenCalledWith('fr');
    });

    it('applies preferredLanguage from server on session restore', async () => {
      const promise = service.restoreSession();
      httpMock.expectOne('/api/auth/me').flush(adminUser);
      await promise;
      expect(translateServiceMock.use).toHaveBeenCalledWith('fr');
    });

    it('resets to en on logout', async () => {
      service.currentUser.set(adminUser);
      const promise = service.logout();
      httpMock.expectOne('/api/auth/logout').flush(null);
      await promise;
      expect(translateServiceMock.use).toHaveBeenCalledWith('en');
    });
  });
});
