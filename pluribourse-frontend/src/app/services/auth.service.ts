import { computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

export interface CurrentUser {
  username: string;
  role: string;
  forcePasswordChange: boolean;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  readonly currentUser = signal<CurrentUser | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  async login(username: string, password: string): Promise<CurrentUser> {
    const body = new URLSearchParams({ username, password });
    const user = await firstValueFrom(
      this.http.post<CurrentUser>('/login', body.toString(), {
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
      })
    );
    this.currentUser.set(user);
    return user;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.http.post<void>('/logout', {}));
    } finally {
      this.currentUser.set(null);
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
