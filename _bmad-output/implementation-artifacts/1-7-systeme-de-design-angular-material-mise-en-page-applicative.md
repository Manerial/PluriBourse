---
baseline_commit: 884fbc050ccf26d3c601bc2bc0e1f73baab789e3
---

# Story 1.7: Angular Material Design System & Application Layout

Status: done

## Story

As a user navigating the application,
I want a visually consistent design adapted to my role with clear navigation,
so that I can instantly find what I need under the pressure of an event day.

## Acceptance Criteria

1. **Given** an authenticated user loads any page, **When** the page renders, **Then** the fixed topbar (56px) is visible with the logo on the left, the phase chip in the center (static text "Préparation" in this story), and the role badge top-right **And** DM Sans font and coral primary `#C44626` are consistently applied across the interface.

2. **Given** an admin is logged in, **When** an admin page loads, **Then** the sidebar (200px, background `#2A100A`) is displayed with "Édition active" and "Gestion" section labels and flat navigation links **And** the currently active route is highlighted in coral (background `#C44626`, white text).

3. **Given** a volunteer is logged in, **When** a volunteer page loads, **Then** no sidebar is displayed — only the topbar.

4. **Given** a button is rendered as a primary action, **When** the button appears, **Then** it uses the filled coral style `#C44626` **And** at most one primary (filled coral) button appears per visible section.

5. **Given** the Angular Material theme is applied, **When** rendered in Chrome, Firefox, Edge, or Safari, **Then** colors, typography, elevation, and border-radius match the design token specifications from DESIGN.md.

## Tasks / Subtasks

- [x] **T1 — `src/index.html`: add Google Fonts** (AC: 1, 5)
  - [x] T1.1 — Add DM Sans font: `<link href="https://fonts.googleapis.com/css2?family=DM+Sans:ital,opsz,wght@0,9..40,400;0,9..40,600;0,9..40,700&display=swap" rel="stylesheet">` in `<head>`
  - [x] T1.2 — Add Material Symbols Outlined font: `<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200" rel="stylesheet">` in `<head>` (required for `<span class="material-symbols-outlined">` icons in sidebar)
  - [x] T1.3 — Update `<title>` to "PluriBourse"

- [x] **T2 — `styles.scss`: Angular Material M3 theme + global styles** (AC: 1, 4, 5)
  - [x] T2.1 — Set up Angular Material M3 theme (see Dev Notes: Angular Material M3 SCSS setup)
  - [x] T2.2 — Override M3 system CSS variables with PluriBourse coral values and define `--pb-*` custom tokens (see Dev Notes: CSS tokens)
  - [x] T2.3 — Add global base styles: `font-family: 'DM Sans', sans-serif`, `box-sizing: border-box`, body margin 0, bg `var(--mat-sys-background)`, color `var(--mat-sys-on-surface)`

- [x] **T3 — `app.config.ts`: add `provideAnimationsAsync()`** (AC: 5)
  - [x] T3.1 — Add `import { provideAnimationsAsync } from '@angular/platform-browser/animations/async'` and add `provideAnimationsAsync()` to the providers array — Angular Material animated components require it

- [x] **T4 — Create `AppLayoutComponent`** (AC: 1, 2, 3)
  - [x] T4.1 — Create `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts` (see Dev Notes: TypeScript)
  - [x] T4.2 — Create `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html` (see Dev Notes: HTML structure)
  - [x] T4.3 — Create `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss` (see Dev Notes: SCSS)

- [x] **T5 — `app.routes.ts`: shell routing pattern** (AC: 1, 2, 3)
  - [x] T5.1 — Replace flat routes with shell pattern: `AppLayoutComponent` as parent for `/admin`, `/account`, `/volunteer`; `/login` and `/change-password` remain outside the shell (see Dev Notes: routing)

- [x] **T6 — i18n keys for navigation** (AC: 1, 2)
  - [x] T6.1 — Add `nav` section to `pluribourse-frontend/public/i18n/en.json` (see Dev Notes: i18n EN)
  - [x] T6.2 — Add `nav` section to `pluribourse-frontend/public/i18n/fr.json` (vouvoiement — see Dev Notes: i18n FR)

- [x] **T7 — `AppLayoutComponent` spec** (coverage ≥ 80%)
  - [x] T7.1 — Create `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts` (Vitest — see Dev Notes: spec pattern)
  - [x] T7.2 — Test: topbar is always rendered for authenticated users
  - [x] T7.3 — Test: sidebar is rendered when `role === 'ADMIN'`
  - [x] T7.4 — Test: sidebar is NOT rendered when `role === 'VOLUNTEER'`
  - [x] T7.5 — Test: role badge shows 'ADMIN' text for admin, 'VOLUNTEER' for volunteer
  - [x] T7.6 — Test: admin sidebar contains nav links to `/admin/users` and `/admin/settings`
  - [x] T7.7 — Test: logout button click calls `auth.logout()`

- [x] **T8 — `admin.routes.ts`: add default redirect** (AC: 2)
  - [x] T8.1 — Add `{ path: '', redirectTo: 'users', pathMatch: 'full' }` as the first entry in `adminRoutes` — without this, navigating to `/admin` (post-login redirect) shows the admin layout with empty content area; the shell routing in T5 makes this bug visible even though it pre-exists Story 1.7

- [x] **T9 — `app.spec.ts`: add `provideRouter([])`** (regression guard)
  - [x] T9.1 — Add `provideRouter` import and `provideRouter([])` to TestBed providers — `RouterOutlet` requires Router in the test environment

## Dev Notes

### Angular Material M3 SCSS setup (T2.1)

Angular Material 21 uses M3 theming. `@angular/cdk` is installed as a peer dependency of `@angular/material` — it is in `node_modules` even if not in `package.json`.

```scss
// styles.scss
@use '@angular/material' as mat;

// M3 theme — red palette is closest to coral
// NOTE: mat.core() is a no-op in Angular Material 21 (deprecated empty stub) — do NOT call it
html {
  @include mat.theme((
    color: (
      theme-type: light,
      primary: mat.$red-palette,
      tertiary: mat.$azure-palette,
    ),
    typography: 'DM Sans',
    density: 0,
  ));
}
```

**VERIFY AFTER `ng serve`:** Inspect DevTools to confirm the actual CSS variable names Angular Material generates (format confirmed: `--mat-sys-primary`, `--mat-sys-on-primary`, etc.). The override block in T2.2 targets these variables.

### CSS Design Token Overrides (T2.2)

Place this block AFTER the `html { @include mat.theme(...) }` block:

```scss
:root {
  // Override M3 primary system variables with PluriBourse coral
  --mat-sys-primary: #C44626;
  --mat-sys-on-primary: #FFFFFF;
  --mat-sys-primary-container: #FFF4EE;
  --mat-sys-on-primary-container: #8C2910;

  // Warm beige surfaces
  --mat-sys-surface: #FFFBF9;
  --mat-sys-on-surface: #1A0A05;
  --mat-sys-surface-variant: #F5EEEA;
  --mat-sys-on-surface-variant: #6B6460;
  --mat-sys-background: #FFFBF9;
  --mat-sys-outline: #8A7870;
  --mat-sys-outline-variant: #F0E4DC;

  // Error
  --mat-sys-error: #BA1A1A;
  --mat-sys-on-error: #FFFFFF;
  --mat-sys-error-container: #FFDAD6;
  --mat-sys-on-error-container: #410002;

  // PluriBourse custom tokens (non-M3)
  --pb-sidebar-bg: #2A100A;
  --pb-primary-hover: #A83A1E;
  --pb-success-container: #F0FDF4;
  --pb-on-success-container: #166534;
  --pb-on-surface-muted: rgba(245, 238, 234, 0.65);

  // Shape tokens
  --pb-rounded-sm: 4px;
  --pb-rounded-md: 8px;
  --pb-rounded-lg: 12px;
  --pb-rounded-xl: 20px;
  --pb-rounded-full: 999px;

  // Spacing tokens
  --pb-space-xs: 4px;
  --pb-space-sm: 8px;
  --pb-space-md: 16px;
  --pb-space-lg: 24px;
  --pb-space-xl: 32px;
  --pb-space-2xl: 48px;
  --pb-space-3xl: 64px;

  // Elevation tokens
  --pb-elevation-1: 0 1px 4px rgba(28,10,5,.08);
  --pb-elevation-2: 0 4px 16px rgba(28,10,5,.14), 0 1px 4px rgba(28,10,5,.08);
  --pb-elevation-3: 0 8px 24px rgba(28,10,5,.18), 0 2px 6px rgba(28,10,5,.10);
}

// Global base styles
*, *::before, *::after { box-sizing: border-box; }

body {
  margin: 0;
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  background-color: var(--mat-sys-background);
  color: var(--mat-sys-on-surface);
  -webkit-font-smoothing: antialiased;
  min-height: 100vh;
}
```

### AppLayoutComponent TypeScript (T4.1)

Use plain CSS layout (no `MatSidenav`) — the sidebar has a dark theme requiring full design control, and v1 is desktop-only.

```typescript
// src/app/layout/app-layout/app-layout.component.ts
import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../../services/auth.service';

// NOTE: MatIcon is NOT used — it requires MatIconRegistry configuration to work with Material Symbols.
// Use <span class="material-symbols-outlined"> directly instead (simpler, font loaded in index.html).
@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './app-layout.component.html',
  styleUrl: './app-layout.component.scss'
})
export class AppLayoutComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly currentUser = this.auth.currentUser;
  readonly isAdmin = computed(() => this.auth.currentUser()?.role === 'ADMIN');

  async logout(): Promise<void> {
    await this.auth.logout();
  }
}
```

### AppLayoutComponent HTML (T4.2)

```html
<!-- src/app/layout/app-layout/app-layout.component.html -->
<div class="app-shell" [class.has-sidebar]="isAdmin()">

  <!-- Topbar (56px fixed) -->
  <header class="topbar" role="banner">
    <div class="topbar__logo">
      <span class="logo-text">PluriBourse</span>
    </div>

    <div class="topbar__center">
      <!-- Phase chip — static in Story 1.7, SSE-driven in Story 2.4 -->
      <span class="phase-chip" [attr.aria-label]="'nav.phase.current' | translate">
        <span class="phase-chip__dot" aria-hidden="true">●</span>
        {{ 'nav.phase.preparation' | translate }}
      </span>
    </div>

    <div class="topbar__actions">
      <span
        class="role-badge"
        [class.role-badge--admin]="isAdmin()"
        aria-live="polite">
        {{ isAdmin() ? ('nav.role.admin' | translate) : ('nav.role.volunteer' | translate) }}
      </span>
      <button class="btn-ghost" type="button" (click)="logout()">
        {{ 'nav.logout' | translate }}
      </button>
    </div>
  </header>

  <!-- Admin sidebar (200px, dark) -->
  @if (isAdmin()) {
    <nav class="sidebar" [attr.aria-label]="'nav.sidebar.label' | translate">

      <div class="sidebar__section">
        <span class="sidebar__section-label">{{ 'nav.sections.activeEdition' | translate }}</span>
        <!-- Edition nav items added in Epic 2 (Story 2.1+) -->
      </div>

      <div class="sidebar__section">
        <span class="sidebar__section-label">{{ 'nav.sections.management' | translate }}</span>

        <a
          routerLink="/admin/users"
          routerLinkActive="sidebar__item--active"
          class="sidebar__item">
          <span class="material-symbols-outlined" aria-hidden="true">group</span>
          <span>{{ 'nav.admin.users' | translate }}</span>
        </a>

        <a
          routerLink="/admin/settings"
          routerLinkActive="sidebar__item--active"
          class="sidebar__item">
          <span class="material-symbols-outlined" aria-hidden="true">settings</span>
          <span>{{ 'nav.admin.settings' | translate }}</span>
        </a>

      </div>
    </nav>
  }

  <!-- Main content area -->
  <main class="content">
    <router-outlet />
  </main>

</div>
```

### AppLayoutComponent SCSS (T4.3)

```scss
// src/app/layout/app-layout/app-layout.component.scss

.app-shell {
  display: grid;
  grid-template-areas:
    'topbar'
    'content';
  grid-template-rows: 56px 1fr;
  grid-template-columns: 1fr;
  min-height: 100vh;

  &.has-sidebar {
    grid-template-areas:
      'topbar topbar'
      'sidebar content';
    grid-template-columns: 200px 1fr;
  }
}

// ── Topbar ────────────────────────────────────────────────────────────────────
.topbar {
  grid-area: topbar;
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 var(--pb-space-md);
  background: var(--mat-sys-surface);
  position: sticky;
  top: 0;
  z-index: 100;

  &__logo {
    flex: 0 0 auto;
  }

  &__center {
    flex: 1;
    display: flex;
    justify-content: center;
  }

  &__actions {
    flex: 0 0 auto;
    display: flex;
    align-items: center;
    gap: var(--pb-space-sm);
  }
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--mat-sys-primary);
  letter-spacing: -0.01em;
}

// ── Phase chip ────────────────────────────────────────────────────────────────
.phase-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--mat-sys-primary-container);
  color: var(--mat-sys-on-primary-container);
  border-radius: var(--pb-rounded-full);
  font-size: 14px;
  font-weight: 600;

  &__dot {
    color: var(--mat-sys-primary);
    font-size: 10px;
    line-height: 1;
  }
}

// ── Role badge ────────────────────────────────────────────────────────────────
.role-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  background: var(--mat-sys-surface-variant);
  color: var(--mat-sys-on-surface-variant);
  border-radius: var(--pb-rounded-full);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;

  &--admin {
    background: var(--mat-sys-primary-container);
    color: var(--mat-sys-on-primary-container);
  }
}

// ── Ghost button (logout) ─────────────────────────────────────────────────────
.btn-ghost {
  background: transparent;
  border: none;
  padding: 9px var(--pb-space-md);
  border-radius: var(--pb-rounded-md);
  color: var(--mat-sys-on-surface-variant);
  font-family: 'DM Sans', sans-serif;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  line-height: 1;

  &:hover { background: var(--mat-sys-surface-variant); }

  &:focus-visible {
    outline: 2px solid var(--mat-sys-primary);
    outline-offset: 2px;
  }
}

// ── Sidebar ───────────────────────────────────────────────────────────────────
.sidebar {
  grid-area: sidebar;
  background: var(--pb-sidebar-bg);
  width: 200px;
  display: flex;
  flex-direction: column;
  padding: var(--pb-space-md) 0;
  overflow-y: auto;

  &__section {
    display: flex;
    flex-direction: column;
    margin-bottom: var(--pb-space-sm);
  }

  &__section-label {
    padding: var(--pb-space-sm) var(--pb-space-md);
    font-size: 12px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--pb-on-surface-muted);
  }

  &__item {
    display: flex;
    align-items: center;
    gap: var(--pb-space-sm);
    padding: 12px var(--pb-space-md);
    color: var(--pb-on-surface-muted);
    text-decoration: none;
    font-size: 14px;
    font-weight: 400;
    border-radius: var(--pb-rounded-md);
    margin: 0 var(--pb-space-xs);

    .material-symbols-outlined {
      font-size: 18px;
      width: 18px;
      height: 18px;
      line-height: 1;
    }

    &:hover { background: rgba(255, 255, 255, 0.08); }

    &:focus-visible {
      outline: 2px solid var(--mat-sys-primary);
      outline-offset: 2px;
    }

    &--active {
      background: var(--mat-sys-primary) !important;
      color: #FFFFFF !important;
    }
  }
}

// ── Content area ──────────────────────────────────────────────────────────────
.content {
  grid-area: content;
  padding: var(--pb-space-lg);
  overflow-y: auto;
}
```

### Shell routing pattern (T5.1)

Replace the entire `app.routes.ts` content with the shell pattern:

```typescript
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { AppLayoutComponent } from './layout/app-layout/app-layout.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'change-password',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/auth/change-password/change-password.component').then(
        m => m.ChangePasswordComponent,
      ),
  },
  {
    path: '',
    component: AppLayoutComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'admin',
        canActivate: [adminGuard],
        loadChildren: () =>
          import('./features/admin/admin.routes').then(m => m.adminRoutes),
      },
      {
        path: 'account',
        loadComponent: () =>
          import('./features/account/account.component').then(m => m.AccountComponent),
      },
      {
        path: 'volunteer',
        loadChildren: () =>
          import('./features/volunteer/volunteer.routes').then(m => m.volunteerRoutes),
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'login',
  },
];
```

**Why this works:**
- Unauthenticated user hits any route → `authGuard` on parent `''` redirects to `/login`
- Authenticated user hits `/login` → `LoginComponent` renders (login logic already redirects to role-based route on success)
- Authenticated admin hits `/admin/users` → parent `authGuard` passes → `adminGuard` passes → page renders with sidebar
- Authenticated volunteer hits `/volunteer` → parent `authGuard` passes → page renders without sidebar
- `adminGuard` remains on the `/admin` child route exactly as before

**REGRESSION CHECK:** The `authGuard` is now on the parent route only, not repeated on `/admin`, `/account`, `/volunteer`. The `adminGuard` stays on `/admin`. Verify the guards still work by testing login → redirect → page load flow manually.

**IMPACT ON `app.spec.ts`:** The `App` component still imports `RouterOutlet` — add `provideRouter([])` to TestBed providers in `app.spec.ts`.

### i18n keys — EN (T6.1)

Add `"nav"` section to `en.json`:

```json
"nav": {
  "logout": "Sign out",
  "phase": {
    "current": "Current phase",
    "preparation": "Preparation",
    "deposit": "Deposit",
    "sale": "Sale",
    "post-sale": "Post-sale",
    "closed": "Closed"
  },
  "role": {
    "admin": "Admin",
    "volunteer": "Volunteer"
  },
  "sections": {
    "activeEdition": "Active Edition",
    "management": "Management"
  },
  "sidebar": {
    "label": "Main navigation"
  },
  "admin": {
    "users": "Volunteers",
    "settings": "Settings"
  }
}
```

### i18n keys — FR (T6.2)

Add `"nav"` section to `fr.json` — **vouvoiement systématique**, phases en français:

```json
"nav": {
  "logout": "Se déconnecter",
  "phase": {
    "current": "Phase actuelle",
    "preparation": "Préparation",
    "deposit": "Dépôt",
    "sale": "Vente",
    "post-sale": "Post-vente",
    "closed": "Clôturée"
  },
  "role": {
    "admin": "Admin",
    "volunteer": "Bénévole"
  },
  "sections": {
    "activeEdition": "Édition active",
    "management": "Gestion"
  },
  "sidebar": {
    "label": "Navigation principale"
  },
  "admin": {
    "users": "Bénévoles",
    "settings": "Paramètres"
  }
}
```

### AppLayoutComponent spec pattern (T7)

Follow the exact Vitest pattern from `account.component.spec.ts` and `admin-settings.component.spec.ts` — NEVER use `jasmine.createSpyObj` or `jest.fn()`.

Key patterns confirmed working (Story 1.6):
- `vi.fn()` for mocks, `signal()` for signal mocks
- `provideTranslateService({ lang: 'en' })` (NOT `TranslateModule.forRoot()`)
- `TranslatePipe` already imported via component's `imports` array
- `provideRouter([])` required when component imports `RouterLink` / `RouterLinkActive` / `RouterOutlet`

```typescript
// src/app/layout/app-layout/app-layout.component.spec.ts
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { signal } from '@angular/core';
import { vi } from 'vitest';
import { AppLayoutComponent } from './app-layout.component';
import { AuthService } from '../../services/auth.service';
import { CurrentUser } from '../../services/auth.service';

const adminUser: CurrentUser = {
  username: 'Admin',
  role: 'ADMIN',
  forcePasswordChange: false,
  preferredLanguage: 'EN'
};

const volunteerUser: CurrentUser = {
  username: 'vol1',
  role: 'VOLUNTEER',
  forcePasswordChange: false,
  preferredLanguage: 'EN'
};

describe('AppLayoutComponent', () => {
  let fixture: ComponentFixture<AppLayoutComponent>;
  let component: AppLayoutComponent;
  const mockLogout = vi.fn().mockResolvedValue(undefined);
  const mockCurrentUser = signal<CurrentUser | null>(adminUser);

  const authServiceMock = {
    currentUser: mockCurrentUser,
    logout: mockLogout,
  };

  beforeEach(async () => {
    mockCurrentUser.set(adminUser);
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [AppLayoutComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: AuthService, useValue: authServiceMock },
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
  });

  it('calls auth.logout() when logout button is clicked', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('.btn-ghost');
    btn.click();
    expect(mockLogout).toHaveBeenCalledOnce();
  });
});
```

### What NOT to change

- `authGuard` or `adminGuard` logic — guards remain unchanged; only routing structure changes
- Any backend file — this story is 100% frontend
- Existing component logic: `LoginComponent`, `ChangePasswordComponent`, `AdminSettingsComponent`, `AccountComponent`, `UserListComponent`, `UserFormComponent` — all work inside the new layout without changes to their TS/HTML
- `app.ts` (`App` component) — it stays `<router-outlet />`; the shell route in `app.routes.ts` wraps auth pages
- `admin.routes.ts` — unchanged; `adminGuard` remains on the `/admin` route
- The existing guard specs (`auth.guard.spec.ts`, `admin.guard.spec.ts`) — no change needed since guards themselves are unchanged
- `auth.interceptor.spec.ts` — no change needed

### Material Symbols icons — do NOT use `MatIcon`

**Why:** `MatIcon` requires explicit `MatIconRegistry` configuration to resolve Material Symbols font sets. Without that configuration, it renders squares. This is verified against Angular Material 21 source.

**Use instead:** `<span class="material-symbols-outlined" aria-hidden="true">icon_name</span>` — the font is loaded via `<link>` in `index.html` (T1.2). No Angular component import needed.

Icon names for sidebar nav items:
- Users management: `group`
- Settings: `settings`

Do NOT add `MatIcon` to any component's `imports` array in this story.

### Previous story learnings (Story 1.6)

- **`provideTranslateService({ lang: 'en' })`** in TestBed — NOT `TranslateModule.forRoot()`
- **`TranslatePipe`** in component imports array — NOT `TranslateModule`
- **Vitest only**: `vi.fn()`, `vi.spyOn()`, `vi.clearAllMocks()` — NEVER `jest.fn()` or `jasmine.createSpyObj()`
- Adding new DI dependencies (like `Router`) to a component can cascade into spec failures in other tests — but `AppLayoutComponent` is a NEW component, so this only affects its own spec
- `provideAnimationsAsync()` was NOT in `app.config.ts` before this story — add it in T3
- The `App` component (`app.ts`) currently only imports `RouterOutlet` — `app.spec.ts` may already require `provideRouter([])` but Story 1.6 completion showed 45/45 tests pass without it; adding `provideRouter([])` in T8 ensures no regression when deeper Router integration occurs

### Design system: applying tokens consistently

For all existing and future components:
- Buttons: primary = `background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); border-radius: var(--pb-rounded-md); padding: 10px 20px`
- Ghost button: `background: transparent; color: var(--mat-sys-on-surface-variant); border-radius: var(--pb-rounded-md); padding: 9px 20px`
- Input fields: `border: 1.5px solid var(--mat-sys-outline); border-radius: var(--pb-rounded-md); padding: 10px 14px; font-size: 14px`
- Cards/panels: `background: var(--mat-sys-surface); border-radius: var(--pb-rounded-lg); box-shadow: var(--pb-elevation-2); padding: var(--pb-space-md)`
- Focus ring (all interactive): `outline: 2px solid var(--mat-sys-primary); outline-offset: 2px`
- **DO NOT** hardcode `#C44626` in component CSS — always use `var(--mat-sys-primary)`

## Project Structure Notes

**New files:**
```
pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts
pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html
pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss
pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts
```

**Modified files:**
```
pluribourse-frontend/src/index.html                     ← DM Sans + Material Symbols fonts, title
pluribourse-frontend/src/styles.scss                    ← Angular Material M3 theme + design tokens
pluribourse-frontend/src/app/app.config.ts              ← add provideAnimationsAsync()
pluribourse-frontend/src/app/app.routes.ts              ← shell routing with AppLayoutComponent
pluribourse-frontend/src/app/features/admin/admin.routes.ts  ← add default redirect to /users
pluribourse-frontend/src/app/app.spec.ts                ← add provideRouter([])
pluribourse-frontend/public/i18n/en.json                ← add nav section
pluribourse-frontend/public/i18n/fr.json                ← add nav section
```

## References

- [Source: epics.md#Story 1.7] — user story, acceptance criteria
- [Source: ux-designs/DESIGN.md] — all design tokens, sidebar structure, component specs, colors
- [Source: 1-6-preference-de-langue-utilisateur-infrastructure-i18n.md] — confirmed Vitest patterns, `provideTranslateService`, signal mocks, `vi.fn()`, `vi.clearAllMocks()`
- [Source: admin-settings.component.ts:1] — reference pattern: standalone, inject, signals, firstValueFrom, FormBuilder.nonNullable
- [Source: app.config.ts] — current providers — `provideAnimationsAsync()` missing, needs adding
- [Source: app.routes.ts] — current flat routes to be replaced with shell pattern
- [Source: auth.service.ts:20] — `currentUser` is a writable `signal<CurrentUser | null>` — inject and use `computed(() => ...)` in layout component
- [Source: styles.scss] — currently empty, needs full M3 theme setup
- [Source: index.html] — currently has no font links, title is "PluribourseFrontend"
- [Source: ux-designs/DESIGN.md#sidebar-item] — active bg `colors.primary`, active fg `#FFFFFF`, hover `rgba(255,255,255,0.08)`, icon 18px
- [Source: ux-designs/DESIGN.md#topbar] — height 56px, bg `colors.surface`, no shadow, sticky

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Completion Notes List
- T1: Updated `index.html` — added DM Sans and Material Symbols Outlined Google Fonts links, updated title to "PluriBourse"
- T2: Rewrote `styles.scss` — Angular Material M3 theme with red palette, full `--mat-sys-*` coral overrides, `--pb-*` custom tokens, global base styles
- T3: Added `provideAnimationsAsync()` to `app.config.ts` — required for Angular Material animated components
- T4: Created `AppLayoutComponent` (TS + HTML + SCSS) — CSS grid shell layout (no MatSidenav), admin sidebar with `@if (isAdmin())`, topbar with phase chip + role badge + logout
- T5: Replaced flat routes in `app.routes.ts` with shell pattern — `AppLayoutComponent` as authenticated parent, `authGuard` on parent only, `adminGuard` stays on `/admin` child
- T6: Added `"nav"` section to `en.json` and `fr.json` — logout, phase labels, role names, section labels, sidebar nav items (vouvoiement in FR)
- T7: Created `app-layout.component.spec.ts` — 10 tests covering topbar, phase chip, admin sidebar (with nav links), volunteer (no sidebar), role badge, logout. Fixed `Language.EN` enum usage (not plain string)
- T8: Added `{ path: '', redirectTo: 'users', pathMatch: 'full' }` to `admin.routes.ts` — fixes empty content on `/admin` redirect post-login
- T9: Added `provideRouter([])` to `app.spec.ts` — prevents regression when `RouterOutlet` integration deepens
- All 55 tests pass (45 existing + 10 new), zero regressions

### File List
- pluribourse-frontend/src/index.html (modified)
- pluribourse-frontend/src/styles.scss (modified)
- pluribourse-frontend/src/app/app.config.ts (modified)
- pluribourse-frontend/src/app/app.routes.ts (modified)
- pluribourse-frontend/src/app/app.spec.ts (modified)
- pluribourse-frontend/src/app/features/admin/admin.routes.ts (modified)
- pluribourse-frontend/public/i18n/en.json (modified)
- pluribourse-frontend/public/i18n/fr.json (modified)
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts (created)
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html (created)
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss (created)
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts (created)

### Review Findings

**Decision-needed**
- [x] [Review][Patch] No default child redirect from shell `''` path — add functional `redirectTo` (admin → /admin, volunteer → /volunteer) [app.routes.ts:21-42]
- [x] [Review][Dismiss] Phase chip i18n approach accepted — "Preparation" in English is correct per i18n design; AC1 intent met

**Patches**
- [x] [Review][Patch] Unused `Router` injection in `AppLayoutComponent` [app-layout.component.ts:17]
- [x] [Review][Patch] `aria-live="polite"` on static role badge — role does not change during session [app-layout.component.html:21]
- [x] [Review][Patch] Active route highlight (`sidebar__item--active`) not tested in spec [app-layout.component.spec.ts]
- [x] [Review][Patch] `--mat-sys-secondary` / `--mat-sys-on-secondary` overrides missing from `:root` block [styles.scss]

**Deferred**
- [x] [Review][Defer] "Édition active" section has no nav links [app-layout.component.html:34-37] — deferred, explicitly deferred to Epic 2 in story notes
- [x] [Review][Defer] Topbar `position: sticky` vs spec "fixed" [app-layout.component.scss:27] — deferred, equivalent behavior in current CSS grid layout
- [x] [Review][Defer] `forcePasswordChange: undefined` in API response bypasses guard redirect [auth.guard.ts] — deferred, pre-existing
- [x] [Review][Defer] Session restore race: valid session user can be bounced to login [auth.guard.ts / app.config.ts] — deferred, pre-existing
- [x] [Review][Defer] `logout()` no error handling / cosmetic role-badge flash before redirect [app-layout.component.ts:22-24] — deferred
- [x] [Review][Defer] Google Fonts loaded without SRI or crossorigin attributes [index.html:9-10] — deferred
- [x] [Review][Defer] CSS dual-source theming (`mat.theme()` + `:root` overrides) brittle on Angular Material upgrade [styles.scss] — deferred
- [x] [Review][Defer] `!important` on `.sidebar__item--active` [app-layout.component.scss] — deferred
- [x] [Review][Defer] `aria-label="Current phase"` on phase chip mismatches visible phase text [app-layout.component.html:13] — deferred
- [x] [Review][Defer] Unused CSS design tokens (`--pb-primary-hover`, `--pb-success-container`, `--pb-on-success-container`) [styles.scss] — deferred, intentional future-use tokens
- [x] [Review][Defer] No `TitleStrategy` for child routes — every page shows "PluriBourse" in browser tab — deferred
- [x] [Review][Defer] `routerLinkActive` prefix match: `/admin/users/create` highlights Users sidebar item — deferred, current design choice
- [x] [Review][Defer] No responsive breakpoint for sidebar layout [app-layout.component.scss] — deferred, desktop-only v1 by design
- [x] [Review][Defer] Logo presence and logout button position not asserted in tests — deferred
- [x] [Review][Defer] `button-primary` CSS class/token absent from stylesheet [styles.scss] — deferred, for future stories with primary action buttons

### Review Findings — Pass 2 (2026-06-24)

**Patches**
- [x] [Review][Dismiss] `/admin/settings` sidebar link — route already exists in `admin.routes.ts` (false positive from partial diff)
- [x] [Review][Patch] `redirectTo` returns `'/volunteer'` when `currentUser()` is `null` — replaced ternary with `switch` on `role`, `default: '/login'` [app.routes.ts:30-35]
- [x] [Review][Patch] `routerLinkActive` missing `ariaCurrentWhenActive="page"` on sidebar `<a>` elements [app-layout.component.html:43,51]
- [x] [Review][Patch] `mockCurrentUser` signal at describe scope — moved to `let`, fresh `signal()` created in each `beforeEach` [app-layout.component.spec.ts]
- [x] [Review][Patch] Wildcard route uses relative `'login'` — changed to absolute `'/login'` [app.routes.ts:53]
- [x] [Review][Patch] `#FFFFFF` hardcoded in `.sidebar__item--active` — replaced with `var(--mat-sys-on-primary)` [app-layout.component.scss:85]
- [x] [Review][Patch] `currentUser` public field on `AppLayoutComponent` unused in template — removed [app-layout.component.ts]
- [x] [Review][Patch] Test: added volunteer role badge presence assertion [app-layout.component.spec.ts]
- [x] [Review][Patch] `app.spec.ts` missing `provideAnimationsAsync()` — added [app.spec.ts]

**Deferred**
- [x] [Review][Defer] `volunteerRoutes` is empty — non-ADMIN redirected to `/volunteer` by `adminGuard` sees blank content area [volunteer.routes.ts] — deferred, pre-existing empty routes, scope Epic 3+
- [x] [Review][Defer] `authInterceptor` clears `currentUser` on any 403 (not only `password-change-required`) — a legitimate admin resource 403 terminates the session [auth.interceptor.ts] — deferred, pre-existing interceptor behaviour
- [x] [Review][Defer] Hard reload with `forcePasswordChange=true` — `restoreSession()` returns early leaving `currentUser` null, `authGuard` blocks `/change-password` [auth.service.ts:63 / auth.guard.ts] — deferred, pre-existing, variant of 1-6 defer

## Change Log

- 2026-06-24 — Story 1.7 created: ready-for-dev
- 2026-06-24 — Story 1.7 verified: 3 patches applied (mat.core() no-op → removed; MatIcon → span.material-symbols-outlined; admin.routes.ts default redirect added as T8)
- 2026-06-24 — Story 1.7 implemented: all 9 tasks complete, 55/55 tests pass → status: review
- 2026-06-24 — Story 1.7 reviewed: 5 patches applied (role redirect, unused Router, aria-live, active route test, secondary colors), 56/56 tests pass → status: done
- 2026-06-24 — Story 1.7 re-reviewed (pass 2): 8 patches applied (switch redirect, ariaCurrentWhenActive, signal scope, wildcard absolute, on-primary token, remove dead field, volunteer badge test, provideAnimationsAsync), 57/57 tests pass → status: done
