import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { Language } from '../models/language.enum';

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

  readonly currentUser = signal<CurrentUser | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  async login(username: string, password: string): Promise<CurrentUser> {
    const body = new URLSearchParams({ username, password });
    const user = await firstValueFrom(
      this.http.post<CurrentUser>('/api/auth/login', body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      })
    );
    this.currentUser.set(user);
    await firstValueFrom(this.translateService.use((user.preferredLanguage ?? Language.EN).toLowerCase()));
    return user;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/api/auth/logout', null));
    } finally {
      this.currentUser.set(null);
      await firstValueFrom(this.translateService.use(Language.EN.toLowerCase()));
      await this.router.navigate(['/login']);
    }
  }

  async changePassword(newPassword: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/auth/change-password', { newPassword }));
    this.currentUser.update(user => user ? { ...user, forcePasswordChange: false } : null);
  }

  async restoreSession(): Promise<void> {
    try {
      const user = await firstValueFrom(this.http.get<CurrentUser>('/api/auth/me'));
      this.currentUser.set(user);
      await firstValueFrom(this.translateService.use((user.preferredLanguage ?? Language.EN).toLowerCase()));
    } catch (error: any) {
      // 403 password-change-required: session is still valid. If currentUser is already
      // populated (e.g., after changePassword), keep it unchanged. On a cold load where
      // currentUser is null, set the sentinel so authGuard can route to /change-password.
      if (error?.status === 403 && error?.error?.type?.includes('password-change-required')) {
        if (!this.currentUser()) {
          this.currentUser.set({ username: '', role: '', forcePasswordChange: true, preferredLanguage: Language.EN });
        }
        return;
      }
      this.currentUser.set(null);
    }
  }
}
