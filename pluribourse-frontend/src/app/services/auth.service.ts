import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { Language } from '../models/language.enum';
import { extractErrorType } from '../shared/http-error.util';
import { detectBrowserLanguage } from '../shared/browser-language.util';
import { PrintService } from './print.service';

export interface CurrentUser {
  username: string;
  role: string;
  forcePasswordChange: boolean;
  preferredLanguage: Language;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly translateService = inject(TranslateService);
  private readonly printService = inject(PrintService);

  private readonly _currentUser = signal<CurrentUser | null>(null);
  readonly currentUser = this._currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  // Defaults to true so ADMIN and any other non-VOLUNTEER role is never blocked by authGuard.
  readonly printerSelectionDone = signal(true);

  clearSession(): void {
    this._currentUser.set(null);
    this.printerSelectionDone.set(true);
  }

  markPrinterSelectionDone(): void {
    this.printerSelectionDone.set(true);
  }

  // Isolated in its own try/catch so a printer-service failure never fails login()/restoreSession()
  // outright, nor gets misclassified by restoreSession()'s 403-password-change catch. Defaults to
  // false on error: worst case the volunteer sees the printer-selection screen again, which is harmless.
  private async refreshPrinterSelectionStatus(user: CurrentUser): Promise<void> {
    if (user.role !== 'VOLUNTEER') {
      this.printerSelectionDone.set(true);
      return;
    }
    try {
      const status = await firstValueFrom(this.printService.getSelectionStatus());
      this.printerSelectionDone.set(status.done);
    } catch {
      this.printerSelectionDone.set(false);
    }
  }

  setPreferredLanguage(lang: Language): void {
    const current = this._currentUser();
    if (current) {
      this._currentUser.set({ ...current, preferredLanguage: lang });
    }
  }

  async login(username: string, password: string): Promise<CurrentUser> {
    const body = new URLSearchParams({ username, password });
    const user = await firstValueFrom(
      this.http.post<CurrentUser>('/api/auth/login', body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      })
    );
    this._currentUser.set(user);
    await firstValueFrom(this.translateService.use((user.preferredLanguage ?? Language.EN).toLowerCase()));
    await this.refreshPrinterSelectionStatus(user);
    return user;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', null));
    } finally {
      this.clearSession();
      await firstValueFrom(this.translateService.use(detectBrowserLanguage().toLowerCase()));
      await this.router.navigate(['/login']);
    }
  }

  async changePassword(newPassword: string): Promise<void> {
    const updatedUser = await firstValueFrom(this.http.post<CurrentUser>('/api/auth/change-password', { newPassword }));
    this._currentUser.set(updatedUser);
  }

  async restoreSession(): Promise<void> {
    try {
      const user = await firstValueFrom(this.http.get<CurrentUser>('/api/auth/me'));
      this._currentUser.set(user);
      await firstValueFrom(this.translateService.use((user.preferredLanguage ?? Language.EN).toLowerCase()));
      await this.refreshPrinterSelectionStatus(user);
    } catch (error) {
      // 403 password-change-required: session is still valid. If currentUser is already
      // populated (e.g., after changePassword), keep it unchanged. On a cold load where
      // currentUser is null, set the sentinel so authGuard can route to /change-password.
      if (error instanceof HttpErrorResponse && error.status === 403 && extractErrorType(error)?.includes('password-change-required')) {
        if (!this.currentUser()) {
          this._currentUser.set({ username: '', role: '', forcePasswordChange: true, preferredLanguage: Language.EN });
        }
        return;
      }
      this._currentUser.set(null);
    }
  }
}
