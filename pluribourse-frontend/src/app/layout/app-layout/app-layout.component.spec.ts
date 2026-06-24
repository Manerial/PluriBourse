import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { Component, signal, WritableSignal } from '@angular/core';
import { vi } from 'vitest';
import { AppLayoutComponent } from './app-layout.component';
import { AuthService, CurrentUser } from '../../services/auth.service';
import { Language } from '../../models/language.enum';

@Component({ standalone: true, template: '' })
class StubComponent {}

const adminUser: CurrentUser = {
  username: 'Admin',
  role: 'ADMIN',
  forcePasswordChange: false,
  preferredLanguage: Language.EN
};

const volunteerUser: CurrentUser = {
  username: 'vol1',
  role: 'VOLUNTEER',
  forcePasswordChange: false,
  preferredLanguage: Language.EN
};

describe('AppLayoutComponent', () => {
  let fixture: ComponentFixture<AppLayoutComponent>;
  let component: AppLayoutComponent;
  let mockCurrentUser: WritableSignal<CurrentUser | null>;
  const mockLogout = vi.fn().mockResolvedValue(undefined);

  beforeEach(async () => {
    mockCurrentUser = signal<CurrentUser | null>(adminUser);
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [AppLayoutComponent],
      providers: [
        provideRouter([
          { path: 'admin/users', component: StubComponent },
          { path: 'admin/settings', component: StubComponent },
        ]),
        provideTranslateService({ lang: 'en' }),
        { provide: AuthService, useValue: { currentUser: mockCurrentUser, logout: mockLogout } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AppLayoutComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => expect(component).toBeTruthy());

  it('renders the topbar', () => {
    expect(fixture.nativeElement.querySelector('.topbar')).toBeTruthy();
  });

  it('renders the phase chip', () => {
    expect(fixture.nativeElement.querySelector('.phase-chip')).toBeTruthy();
  });

  describe('when admin is logged in', () => {
    it('renders the sidebar', () => {
      expect(fixture.nativeElement.querySelector('.sidebar')).toBeTruthy();
    });

    it('contains nav link to /admin/users', () => {
      const links: HTMLAnchorElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('a.sidebar__item')
      );
      expect(links.some(l => l.getAttribute('href') === '/admin/users')).toBe(true);
    });

    it('contains nav link to /admin/settings', () => {
      const links: HTMLAnchorElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('a.sidebar__item')
      );
      expect(links.some(l => l.getAttribute('href') === '/admin/settings')).toBe(true);
    });

    it('renders admin role badge', () => {
      const badge = fixture.nativeElement.querySelector('.role-badge');
      expect(badge).toBeTruthy();
      expect(badge.classList).toContain('role-badge--admin');
    });

    it('applies active class to the sidebar link matching the current route', async () => {
      const router = TestBed.inject(Router);
      await router.navigateByUrl('/admin/users');
      fixture.detectChanges();
      const usersLink: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/admin/users"]');
      expect(usersLink.classList).toContain('sidebar__item--active');
      const settingsLink: HTMLAnchorElement = fixture.nativeElement.querySelector('a[href="/admin/settings"]');
      expect(settingsLink.classList).not.toContain('sidebar__item--active');
    });
  });

  describe('when volunteer is logged in', () => {
    beforeEach(() => {
      mockCurrentUser.set(volunteerUser);
      fixture.detectChanges();
    });

    it('does not render the sidebar', () => {
      expect(fixture.nativeElement.querySelector('.sidebar')).toBeFalsy();
    });

    it('does not apply admin class to role badge', () => {
      const badge = fixture.nativeElement.querySelector('.role-badge');
      expect(badge.classList).not.toContain('role-badge--admin');
    });

    it('shows the role badge', () => {
      expect(fixture.nativeElement.querySelector('.role-badge')).toBeTruthy();
    });
  });

  it('calls auth.logout() when logout button is clicked', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-ghost');
    btn.click();
    expect(mockLogout).toHaveBeenCalledOnce();
  });
});
