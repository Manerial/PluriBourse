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
      await firstValueFrom(this.http.post<void>('/api/auth/logout', {}));
    } finally {
      this.currentUser.set(null);
      this.translateService.use('en').subscribe();
      await this.router.navigate(['/login']);
    }
  }

  async changePassword(newPassword: string): Promise<void> {
    await firstValueFrom(this.http.post<void>('/api/auth/change-password', { newPassword }));
    const user = this.currentUser();
    if (user) {
      this.currentUser.set({ ...user, forcePasswordChange: false });
    }
  }

  async restoreSession(): Promise<void> {
    try {
      const user = await firstValueFrom(this.http.get<CurrentUser>('/api/auth/me'));
      this.currentUser.set(user);
      await firstValueFrom(this.translateService.use((user.preferredLanguage ?? Language.EN).toLowerCase()));
    } catch (error: any) {
      // 403 password-change-required means the session is valid but the password must change.
      // Keep currentUser non-null so authGuard lets the user through to /change-password.
      if (error?.status === 403 && error?.error?.type?.includes('password-change-required')) {
        return;
      }
      this.currentUser.set(null);
    }
  }
}
