import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService, TranslateService } from '@ngx-translate/core';
import { Component, signal, WritableSignal } from '@angular/core';
import { vi } from 'vitest';
import { of, EMPTY } from 'rxjs';
import { AppLayoutComponent } from './app-layout.component';
import { AuthService, CurrentUser } from '../../services/auth.service';
import { Language } from '../../models/language.enum';
import { EditionDto } from '../../models/edition.model';
import { CurrentEditionService } from '../../services/current-edition.service';
import { SseService } from '../../services/sse.service';

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

const preparationEdition: EditionDto = {
  id: 42,
  name: 'Bourse 2026',
  phase: 'PREPARATION',
  commissionRate: 15,
  documentLanguage: Language.FR,
  createdAt: '2026-01-01',
  archived: false,
  startDate: null,
  endDate: null,
};

describe('AppLayoutComponent', () => {
  let fixture: ComponentFixture<AppLayoutComponent>;
  let component: AppLayoutComponent;
  let mockCurrentUser: WritableSignal<CurrentUser | null>;
  let mockEdition: WritableSignal<EditionDto | null>;
  let mockCurrentEditionService: { currentEdition: WritableSignal<EditionDto | null>; loadEdition: ReturnType<typeof vi.fn>; updateFromEvent: ReturnType<typeof vi.fn> };
  const mockLogout = vi.fn().mockResolvedValue(undefined);

  beforeEach(async () => {
    mockCurrentUser = signal<CurrentUser | null>(adminUser);
    mockEdition = signal<EditionDto | null>(null);
    mockCurrentEditionService = {
      currentEdition: mockEdition,
      loadEdition: vi.fn().mockReturnValue(of(undefined)),
      updateFromEvent: vi.fn(),
    };
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [AppLayoutComponent],
      providers: [
        provideRouter([
          { path: 'admin/users', component: StubComponent },
          { path: 'admin/settings', component: StubComponent },
          { path: 'admin/editions', component: StubComponent },
          { path: 'admin/editions/:id/phase', component: StubComponent },
          { path: 'volunteer/deposit', component: StubComponent },
          { path: 'account', component: StubComponent },
          { path: '404', component: StubComponent },
        ]),
        provideTranslateService({ lang: 'en' }),
        { provide: AuthService, useValue: { currentUser: mockCurrentUser, logout: mockLogout } },
        { provide: CurrentEditionService, useValue: mockCurrentEditionService },
        { provide: SseService, useValue: { phaseChanges: () => EMPTY } },
      ],
    }).compileComponents();

    TestBed.inject(TranslateService).setTranslation('en', {
      edition: { phase: { PREPARATION: 'Preparation' } },
      nav: { phase: { none: 'No active edition' } },
    });

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

  describe('phase chip — no active edition', () => {
    it('shows nav.phase.none text and is not a link', () => {
      mockEdition.set(null);
      fixture.detectChanges();
      const chip: HTMLElement = fixture.nativeElement.querySelector('.phase-chip');
      expect(chip.tagName).toBe('SPAN');
      expect(chip.classList).toContain('phase-chip--inactive');
      expect(chip.textContent).toContain('No active edition');
    });
  });

  describe('phase chip — admin with active edition', () => {
    beforeEach(() => {
      mockEdition.set(preparationEdition);
      fixture.detectChanges();
    });

    it('renders a link navigating to /admin/editions/:id/phase', () => {
      const chip: HTMLAnchorElement = fixture.nativeElement.querySelector('a.phase-chip');
      expect(chip).toBeTruthy();
      expect(chip.getAttribute('href')).toBe('/admin/editions/42/phase');
    });

    it('renders the translated phase text', () => {
      const chip: HTMLAnchorElement = fixture.nativeElement.querySelector('a.phase-chip');
      expect(chip.textContent).toContain('Preparation');
    });
  });

  describe('phase chip — volunteer with active edition', () => {
    beforeEach(() => {
      mockCurrentUser.set(volunteerUser);
      mockEdition.set(preparationEdition);
      fixture.detectChanges();
    });

    it('renders a span (not a link) even when edition is active', () => {
      const link = fixture.nativeElement.querySelector('a.phase-chip');
      expect(link).toBeFalsy();
      const span = fixture.nativeElement.querySelector('span.phase-chip:not(.phase-chip--inactive)');
      expect(span).toBeTruthy();
    });
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

    it('contains nav link to /admin/editions', () => {
      const links: HTMLAnchorElement[] = Array.from(
        fixture.nativeElement.querySelectorAll('a.sidebar__item')
      );
      expect(links.some(l => l.getAttribute('href') === '/admin/editions')).toBe(true);
    });

    it('renders admin role badge', () => {
      const badge = fixture.nativeElement.querySelector('.badge');
      expect(badge).toBeTruthy();
      expect(badge.classList).toContain('badge--admin');
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
      const badge = fixture.nativeElement.querySelector('.badge');
      expect(badge.classList).not.toContain('badge--admin');
    });

    it('shows the role badge', () => {
      expect(fixture.nativeElement.querySelector('.badge')).toBeTruthy();
    });
  });

  it('calls currentEditionService.loadEdition() on ngOnInit', () => {
    expect(mockCurrentEditionService.loadEdition).toHaveBeenCalledOnce();
  });

  describe('reactive redirect for volunteers when the phase changes underneath them', () => {
    beforeEach(() => {
      mockCurrentUser.set(volunteerUser);
      fixture.detectChanges();
    });

    it('redirects away from /volunteer/deposit once the phase leaves Dépôt', async () => {
      const router = TestBed.inject(Router);
      mockEdition.set({ ...preparationEdition, phase: 'DEPOSIT' });
      fixture.detectChanges();
      await router.navigateByUrl('/volunteer/deposit');
      fixture.detectChanges();

      mockEdition.set({ ...preparationEdition, phase: 'PREPARATION' });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(router.url).toBe('/404');
    });

    it('redirects from /404 to /volunteer/deposit once the phase reaches Dépôt', async () => {
      const router = TestBed.inject(Router);
      mockEdition.set({ ...preparationEdition, phase: 'PREPARATION' });
      fixture.detectChanges();
      await router.navigateByUrl('/404');
      fixture.detectChanges();

      mockEdition.set({ ...preparationEdition, phase: 'DEPOSIT' });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(router.url).toBe('/volunteer/deposit');
    });

    it('does not redirect away from unrelated pages such as /account', async () => {
      const router = TestBed.inject(Router);
      mockEdition.set({ ...preparationEdition, phase: 'DEPOSIT' });
      fixture.detectChanges();
      await router.navigateByUrl('/account');
      fixture.detectChanges();

      mockEdition.set({ ...preparationEdition, phase: 'PREPARATION' });
      fixture.detectChanges();
      await fixture.whenStable();

      expect(router.url).toBe('/account');
    });
  });

  it('calls auth.logout() when logout button is clicked', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[mat-button]');
    btn.click();
    expect(mockLogout).toHaveBeenCalledOnce();
  });
});
