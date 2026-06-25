---
baseline_commit: 72e9b4018b3ea44fcb2bae248de570431d88195c
---

# Story 1.8: Shared UI Components — Confirmation Dialogs, Notifications & Accessibility

Status: done

## Story

As a user performing operations in the application,
I want clear feedback, accessible confirmations, and helpful empty states,
so that I can act with confidence without making accidental errors under pressure.

## Acceptance Criteria

1. **Given** an irreversible action is triggered, **When** the confirmation dialog appears, **Then** it displays a title, a consequence description, a confirm button, and a cancel (ghost) button **And** focus is trapped inside the dialog **And** initial focus is on the cancel button **And** pressing Escape closes the dialog without acting **And** on close (any cause) focus returns to the trigger element.

2. **Given** a successful operation completes, **When** the result is returned, **Then** a success toast appears bottom-right for 4 seconds then auto-dismisses **And** at most one toast is visible at any time.

3. **Given** a system error occurs (printer offline, network failure), **When** it is surfaced to the user, **Then** a persistent error toast appears bottom-right with a "Close" button that must be clicked to dismiss it.

4. **Given** a business error occurs inline in a workflow, **When** the error is triggered, **Then** an inline notification appears directly under the trigger element (not a toast) **And** it persists until the error is resolved or a new action is taken.

5. **Given** a list loads its initial data, **When** the API request is in progress, **Then** 3 to 5 skeleton rows are displayed and no global spinner blocks the UI.

6. **Given** a list contains no elements, **When** the empty state is displayed, **Then** a centered Material icon, a descriptive message, and a primary action button (if applicable) are shown.

7. **Given** an element receives focus via Tab key, **When** focus lands on a button, link, or input, **Then** a visible focus ring (coral primary, never removed) is displayed **And** all interactive elements have a minimum 44×44px touch target **And** decorative icons have `aria-hidden="true"` **And** semantic icons have an `aria-label` or visible accompanying text.

8. **Given** a user navigates to `/login` or `/change-password`, **When** the page renders, **Then** the form is displayed in a centered card (max-width 400px, `var(--pb-elevation-2)` shadow) on the page background **And** inputs use `mat-form-field` with `matInput` **And** the submit button uses the `.btn-primary` global class.

## Tasks / Subtasks

- [x] **T1 — `styles.scss`: accessibility baseline + deferred fixes** (AC: 7, UX-DR20)
  - [x] T1.1 — Update secondary color values in the `:root` block to match UX spec — the values added by Story 1.7 review are approximations; replace with exact UX spec values: `--mat-sys-secondary: #8C5C42; --mat-sys-on-secondary: #FFFFFF; --mat-sys-secondary-container: #FFDCC5; --mat-sys-on-secondary-container: #331200` (current values: `#8C5C4E`, `#F5EEEA`, `#3D1A10`)
  - [x] T1.2 — Add `.btn-primary` global CSS class (deferred from Story 1.7 Review): `background: var(--mat-sys-primary); color: var(--mat-sys-on-primary); border-radius: var(--pb-rounded-md); padding: 10px 20px; min-height: 44px; font: 14px/600 'DM Sans', sans-serif; cursor: pointer; border: none`
  - [x] T1.3 — Add `.btn-error` global class (same layout as `.btn-primary` but `background: var(--mat-sys-error); color: var(--mat-sys-on-error)`)
  - [x] T1.4 — Add `.btn-ghost` global class to `styles.scss` (copy from `app-layout.component.scss` lines ~94–112): `background: transparent; border: none; padding: 9px var(--pb-space-md); border-radius: var(--pb-rounded-md); color: var(--mat-sys-on-surface-variant); font-family: 'DM Sans', sans-serif; font-size: 14px; font-weight: 600; cursor: pointer; line-height: 1;` + hover state. **Required because `ConfirmDialogComponent` uses `.btn-ghost` on its cancel button but component-scoped styles cannot cross View Encapsulation boundaries — without this, the cancel button has zero styling.**
  - [x] T1.5 — Add global focus ring rule: `a:focus-visible, button:focus-visible, input:focus-visible, select:focus-visible, textarea:focus-visible, [tabindex]:focus-visible { outline: 2px solid var(--mat-sys-primary); outline-offset: 2px; }` — this is the WCAG 2.2 SC 2.4.11 baseline that must never be suppressed
  - [x] T1.6 — Add `.sr-only` utility class: `position: absolute; width: 1px; height: 1px; padding: 0; margin: -1px; overflow: hidden; clip: rect(0,0,0,0); white-space: nowrap; border: 0` — required for screen reader announcements in ToastContainer (T4)
  - [x] T1.7 — Remove the now-redundant `.btn-ghost` block from `app-layout.component.scss` (the global class from T1.4 replaces it; the component-scoped version has higher specificity via encapsulation attributes and would otherwise produce inconsistency). Also remove the duplicate `&:focus-visible` rule from `.btn-ghost` and `.sidebar__item` in `app-layout.component.scss` if they conflict with the global focus rule from T1.5.

- [x] **T2 — `ConfirmDialogComponent` + `ConfirmDialogService`** (AC: 1, UX-DR6)
  - [x] T2.1 — Create `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts` (see Dev Notes: ConfirmDialogComponent TypeScript)
  - [x] T2.2 — Create `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.html` (see Dev Notes: ConfirmDialogComponent HTML)
  - [x] T2.3 — Create `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.scss` (see Dev Notes: ConfirmDialogComponent SCSS)
  - [x] T2.4 — Create `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts` (see Dev Notes: ConfirmDialogService)
  - [x] T2.5 — Add CDK Dialog global styles to `styles.scss`: `.dialog-backdrop { background: rgba(0,0,0,0.5); }` — this class is referenced in `ConfirmDialogService.open()` as `backdropClass`

- [x] **T3 — `NotificationInlineComponent`** (AC: 4, UX-DR7)
  - [x] T3.1 — Create `pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.ts` (see Dev Notes: NotificationInlineComponent)
  - [x] T3.2 — Create `pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.html`
  - [x] T3.3 — Create `pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.scss`

- [x] **T4 — `ToastService` + `ToastContainerComponent` + update `AppLayoutComponent`** (AC: 2, 3, UX-DR8)
  - [x] T4.1 — Create `pluribourse-frontend/src/app/shared/components/toast/toast.service.ts` (see Dev Notes: ToastService)
  - [x] T4.2 — Create `pluribourse-frontend/src/app/shared/components/toast/toast-container.component.ts` (see Dev Notes: ToastContainerComponent TypeScript)
  - [x] T4.3 — Create `pluribourse-frontend/src/app/shared/components/toast/toast-container.component.html` (see Dev Notes: ToastContainerComponent HTML)
  - [x] T4.4 — Create `pluribourse-frontend/src/app/shared/components/toast/toast-container.component.scss` (see Dev Notes: ToastContainerComponent SCSS)
  - [x] T4.5 — Update `app-layout.component.ts`: import `ToastContainerComponent` and add to `imports` array
  - [x] T4.6 — Update `app-layout.component.html`: add `<app-toast-container />` as last child of `.app-shell` div

- [x] **T5 — `SkeletonRowComponent`** (AC: 5, UX-DR12)
  - [x] T5.1 — Create `pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.ts` (see Dev Notes: SkeletonRowComponent)
  - [x] T5.2 — Create `pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.html`
  - [x] T5.3 — Create `pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.scss`

- [x] **T6 — `EmptyStateComponent`** (AC: 6, UX-DR13)
  - [x] T6.1 — Create `pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.ts` (see Dev Notes: EmptyStateComponent)
  - [x] T6.2 — Create `pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.html`
  - [x] T6.3 — Create `pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.scss`

- [x] **T7 — i18n keys for shared components** (AC: all)
  - [x] T7.1 — Add `common.dialog` section to `pluribourse-frontend/public/i18n/en.json` (see Dev Notes: i18n keys)
  - [x] T7.2 — Add `common.dialog` section to `pluribourse-frontend/public/i18n/fr.json`
  - [x] T7.3 — Add `common.toast` section to both JSON files
  - [x] T7.4 — Add `common.empty` section to both JSON files

- [x] **T8 — Specs** (coverage ≥ 80%)
  - [x] T8.1 — Create `confirm-dialog.component.spec.ts` (see Dev Notes: spec patterns)
  - [x] T8.2 — Create `confirm-dialog.service.spec.ts`
  - [x] T8.3 — Create `notification-inline.component.spec.ts`
  - [x] T8.4 — Create `toast.service.spec.ts`
  - [x] T8.5 — Create `toast-container.component.spec.ts`
  - [x] T8.6 — Create `skeleton-row.component.spec.ts`
  - [x] T8.7 — Create `empty-state.component.spec.ts`
  - [x] T8.8 — Run `npm test` in `pluribourse-frontend/` — all 56 existing tests must still pass, zero regressions
  - [x] T8.9 — Create `login.component.spec.ts` (see Dev Notes: auth spec patterns)
  - [x] T8.10 — Create `change-password.component.spec.ts` (see Dev Notes: auth spec patterns)

- [x] **T9 — Auth page styling: LoginComponent + ChangePasswordComponent** (AC: 8)
  - [x] T9.1 — Create `pluribourse-frontend/src/app/features/auth/login/login.component.scss` (see Dev Notes: Auth page SCSS)
  - [x] T9.2 — Update `login.component.ts`: add `styleUrl: './login.component.scss'` and add `MatFormFieldModule`, `MatInputModule` to `imports` array
  - [x] T9.3 — Update `login.component.html` to use `mat-form-field` + `matInput` and `.btn-primary` button (see Dev Notes: LoginComponent HTML)
  - [x] T9.4 — Create `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.scss` (identical SCSS to T9.1 — see Dev Notes: Auth page SCSS)
  - [x] T9.5 — Update `change-password.component.ts`: add `styleUrl: './change-password.component.scss'` and add `MatFormFieldModule`, `MatInputModule` to `imports` array
  - [x] T9.6 — Update `change-password.component.html` to use `mat-form-field` + `matInput` and `.btn-primary` button (see Dev Notes: ChangePasswordComponent HTML)

### Review Findings

- [x] [Review][Patch] **[CRITIQUE] Régression auth.service restoreSession — sentinelle 403 supprimée** : en cold load, `currentUser` reste `null` → `authGuard` redirige vers `/login` au lieu de `/change-password` [`auth.service.ts:59-61`]
- [x] [Review][Patch] **RangeError si rows ≤ 0 dans SkeletonRowComponent** : `Array(-1).fill(0)` lève une `RangeError` en runtime [`skeleton-row.component.ts:12`]
- [x] [Review][Patch] **Faux toast d'erreur si `translateService.use()` échoue après `updateLanguage` réussi** : le `catch` affiche `showError('account.error.save')` pour une sauvegarde qui a bien fonctionné côté serveur [`account.component.ts`]
- [x] [Review][Patch] **`role="dialog"` dupliqué — div interne ET panel CDK** : faux positif — le fichier implémenté n'a pas `role="dialog"` sur la div interne ; l'auditeur avait lu les Dev Notes, pas le fichier réel. [`confirm-dialog.component.html`]
- [x] [Review][Patch] **AC7 — cibles tactiles < 44 × 44 px** : `.btn-ghost` ≈ 32 px, `.btn-icon` ≈ 40 px, `.toast__close` ≈ 26 px — aucun n'a de `min-height` explicite [`styles.scss`, `toast-container.component.scss`]
- [x] [Review][Patch] **AC7 — focus ring de `.toast__close` remplace le corail global par `currentColor`** : le style component-scoped override la règle globale `var(--mat-sys-primary)` [`toast-container.component.scss:45-48`]
- [x] [Review][Patch] **`provideAnimationsAsync()` manquant dans `admin-settings.component.spec.ts`** : faux positif — Angular Material 21 ne requiert pas de provider d'animation explicite ; les tests passent sans. Pattern hérité de la story 1.7, non applicable ici. [`admin-settings.component.spec.ts`]
- [x] [Review][Patch] **Aucun style `:disabled` sur `.btn-primary` / `.btn-error` (WCAG 1.4.1/1.4.3)** : les boutons `[disabled]` sont visuellement identiques aux boutons actifs [`styles.scss`]
- [x] [Review][Patch] **Classe CSS morte `.auth-card__error`** : définie dans `styles.scss` mais aucun template ne l'utilise (login et change-password utilisent `<app-notification-inline>`) [`styles.scss`]
- [x] [Review][Patch] **Clé i18n morte `admin.users.columns.username`** : la colonne a été fusionnée dans la cellule nom, la clé de l'en-tête de colonne n'existe plus dans le template [`en.json`, `fr.json`]
- [x] [Review][Defer] confirm-dialog — fermeture via backdrop émet `undefined` ; aucun appelant dans ce diff [`confirm-dialog.service.ts`] — deferred, pre-existing
- [x] [Review][Defer] Toast `z-index: 200` — nombre magique sans token CSS `--pb-*` [`toast-container.component.scss`] — deferred, pre-existing
- [x] [Review][Defer] `ToastService` — pas de nettoyage du timer sur destroy (`DestroyRef`) [`toast.service.ts`] — deferred, pre-existing
- [x] [Review][Defer] Sidebar — focus ring corail potentiellement invisible sur fond sombre après suppression du style scoped [`app-layout.component.scss`] — deferred, pre-existing
- [x] [Review][Defer] `user-list` — route `/admin/users/create` codée en dur (vs `routerLink` relatif) [`user-list.component.ts`] — deferred, pre-existing
- [x] [Review][Defer] `user-list` — champ reset-password utilise `<input>` brut au lieu de `mat-form-field` [`user-list.component.html`] — deferred, pre-existing
- [x] [Review][Defer] Auth — erreur affichée via `<app-notification-inline>` (écart spec T9.3/T9.6, amélioration intentionnelle) [`login.component.html`, `change-password.component.html`] — deferred, pre-existing
- [x] [Review][Defer] T9.5 — `ChangePasswordComponent` : injections `ToastService` / `TranslateService` et logique toast hors périmètre spec [`change-password.component.ts`] — deferred, pre-existing
- [x] [Review][Defer] `change-password` — `toast.showSuccess()` déclenché avant `await router.navigate()` [`change-password.component.ts:37`] — deferred, pre-existing

## Dev Notes

### Angular CDK Dialog — available, use it for ConfirmDialog

`@angular/cdk` is installed as a peer dependency of `@angular/material` — it is in `node_modules` even if not listed in `package.json`. **Do NOT `npm install @angular/cdk`** — it is already present.

Use `@angular/cdk/dialog` for `ConfirmDialogComponent`. CDK `Dialog` provides:
- Automatic focus trap (SC 2.1.2, SC 2.4.3)
- Escape key handling via `disableClose: false`
- Return focus to trigger on close (when trigger element is tracked by CDK)
- Backdrop management

**Import path**: `import { Dialog, DIALOG_DATA, DialogRef } from '@angular/cdk/dialog'`

### ConfirmDialogComponent TypeScript (T2.1)

```typescript
// src/app/shared/components/confirm-dialog/confirm-dialog.component.ts
import { Component, inject } from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslatePipe } from '@ngx-translate/core';

export interface ConfirmDialogData {
  title: string;           // pre-translated string
  description: string;     // pre-translated string
  confirmLabel?: string;   // pre-translated, defaults applied by service
  cancelLabel?: string;    // pre-translated, defaults applied by service
  confirmVariant?: 'primary' | 'error'; // default: 'primary'
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './confirm-dialog.component.html',
  styleUrl: './confirm-dialog.component.scss',
})
export class ConfirmDialogComponent {
  readonly dialogRef = inject<DialogRef<boolean>>(DialogRef);
  readonly data = inject<ConfirmDialogData>(DIALOG_DATA);

  confirm(): void {
    this.dialogRef.close(true);
  }

  cancel(): void {
    this.dialogRef.close(false);
  }
}
```

**Callers pass pre-translated strings** — they use `TranslateService.instant()` or `| translate` pipe before calling the service. This allows composing dynamic strings (e.g., phase name interpolation).

### ConfirmDialogComponent HTML (T2.2)

```html
<!-- src/app/shared/components/confirm-dialog/confirm-dialog.component.html -->
<div class="dialog" role="dialog" [attr.aria-labelledby]="'dialog-title'" [attr.aria-describedby]="'dialog-desc'">
  <h2 id="dialog-title" class="dialog__title">{{ data.title }}</h2>
  <p id="dialog-desc" class="dialog__description">{{ data.description }}</p>
  <div class="dialog__actions">
    <!-- Cancel button gets initial focus via cdkFocusInitial -->
    <button
      type="button"
      class="btn-ghost"
      cdkFocusInitial
      (click)="cancel()">
      {{ data.cancelLabel ?? ('common.dialog.cancel' | translate) }}
    </button>
    <button
      type="button"
      [class]="data.confirmVariant === 'error' ? 'btn-error' : 'btn-primary'"
      (click)="confirm()">
      {{ data.confirmLabel ?? ('common.dialog.confirm' | translate) }}
    </button>
  </div>
</div>
```

**Critical**: `cdkFocusInitial` directive on the cancel button — this is a CDK directive that sets initial focus when the dialog opens. Import: add `CdkFocusInitial` from `@angular/cdk/a11y` to component imports array.

Wait — `cdkFocusInitial` is an attribute directive from `@angular/cdk/a11y`. To use it, add `CdkTrapFocus` is not needed (CDK Dialog handles this automatically), but `cdkFocusInitial` requires importing the `A11yModule` or `CdkFocusInitial` directive.

**Correct import for `cdkFocusInitial`**:
```typescript
import { CdkTrapFocusModule } from '@angular/cdk/a11y'; // or
import { A11yModule } from '@angular/cdk/a11y'; // or individual directive
```

In Angular 21 with standalone components, import `A11yModule` from `@angular/cdk/a11y` in the `imports` array. This gives access to `cdkFocusInitial`.

Updated component imports:
```typescript
import { A11yModule } from '@angular/cdk/a11y';
imports: [TranslatePipe, A11yModule],
```

### ConfirmDialogComponent SCSS (T2.3)

```scss
// src/app/shared/components/confirm-dialog/confirm-dialog.component.scss

.dialog {
  background: var(--mat-sys-surface);
  border-radius: var(--pb-rounded-xl);     // 20px
  box-shadow: var(--pb-elevation-3);
  padding: var(--pb-space-lg);             // 24px
  max-width: 480px;
  width: 100%;
  outline: none;                           // suppress CDK outline on container

  &__title {
    margin: 0 0 var(--pb-space-sm);
    font-size: 18px;
    font-weight: 600;
    color: var(--mat-sys-on-surface);
  }

  &__description {
    margin: 0 0 var(--pb-space-lg);
    font-size: 14px;
    color: var(--mat-sys-on-surface-variant);
    line-height: 1.5;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--pb-space-sm);
  }
}
```

**CDK Dialog overlay backdrop**: CDK does NOT add a dark backdrop by default — configure via `overlayConfig.backdropClass` or `hasBackdrop` in the service. See ConfirmDialogService notes.

### ConfirmDialogService (T2.4)

```typescript
// src/app/shared/components/confirm-dialog/confirm-dialog.service.ts
import { inject, Injectable } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { Observable } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly dialog = inject(Dialog);

  open(data: ConfirmDialogData): Observable<boolean | undefined> {
    const ref = this.dialog.open<boolean, ConfirmDialogData, ConfirmDialogComponent>(
      ConfirmDialogComponent,
      {
        data,
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,    // allows Escape key to close
      }
    );
    return ref.closed;
  }
}
```

**Global styles for CDK Dialog backdrop** — add to `styles.scss` (outside `:root`):
```scss
// CDK Dialog backdrop
.dialog-backdrop {
  background: rgba(0, 0, 0, 0.5);
}

// CDK Dialog panel — remove default padding/margins
.dialog-panel {
  // CDK applies minimal wrapper; our .dialog component provides all styling
}
```

**How callers use it** (for dev reference, NOT to implement now):
```typescript
// In a future component (Epic 2+):
private confirmDialog = inject(ConfirmDialogService);
private translate = inject(TranslateService);

onPhaseTransition(): void {
  this.confirmDialog.open({
    title: this.translate.instant('phase.confirmTitle'),
    description: this.translate.instant('phase.confirmDescription', { phase: 'Vente' }),
    confirmVariant: 'primary',
  }).subscribe(confirmed => {
    if (confirmed) { /* proceed */ }
  });
}
```

### NotificationInlineComponent (T3)

```typescript
// src/app/shared/components/notification-inline/notification-inline.component.ts
import { Component, input } from '@angular/core';

@Component({
  selector: 'app-notification-inline',
  standalone: true,
  imports: [],
  templateUrl: './notification-inline.component.html',
  styleUrl: './notification-inline.component.scss',
})
export class NotificationInlineComponent {
  readonly message = input.required<string>();
  readonly variant = input<'warning' | 'error'>('warning');
}
```

```html
<!-- notification-inline.component.html -->
<div
  class="notification"
  [class.notification--error]="variant() === 'error'"
  role="status"
  [attr.aria-live]="variant() === 'error' ? 'assertive' : 'polite'">
  <span class="material-symbols-outlined notification__icon" aria-hidden="true">
    {{ variant() === 'error' ? 'error' : 'warning' }}
  </span>
  <span class="notification__message">{{ message() }}</span>
</div>
```

```scss
// notification-inline.component.scss
.notification {
  display: flex;
  align-items: flex-start;
  gap: var(--pb-space-sm);
  padding: var(--pb-space-sm) var(--pb-space-md);
  background: var(--mat-sys-primary-container);
  border-left: 3px solid var(--mat-sys-primary);
  border-radius: 0 var(--pb-rounded-md) var(--pb-rounded-md) 0;
  font-size: 14px;
  color: var(--mat-sys-on-primary-container);
  margin-top: var(--pb-space-xs);

  &--error {
    background: var(--mat-sys-error-container);
    border-left-color: var(--mat-sys-error);
    color: var(--mat-sys-on-error-container);
  }

  &__icon {
    font-size: 18px;
    flex-shrink: 0;
    margin-top: 1px;
  }

  &__message {
    line-height: 1.4;
  }
}
```

**Usage pattern** (caller adds/removes component via `@if`):
```html
<!-- In a calling template -->
@if (errorMessage()) {
  <app-notification-inline [message]="errorMessage()!" />
}
<!-- For error variant -->
@if (conflictError()) {
  <app-notification-inline [message]="conflictError()!" variant="error" />
}
```

### ToastService (T4.1)

```typescript
// src/app/shared/components/toast/toast.service.ts
import { inject, Injectable, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export interface Toast {
  message: string;
  type: 'success' | 'error';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toast = signal<Toast | null>(null);
  readonly toast = this._toast.asReadonly();
  private _timer: ReturnType<typeof setTimeout> | null = null;

  showSuccess(message: string): void {
    this._clearTimer();
    this._toast.set({ message, type: 'success' });
    this._timer = setTimeout(() => this._toast.set(null), 4000);
  }

  showError(message: string): void {
    this._clearTimer();
    this._toast.set({ message, type: 'error' });
    // persistent — no auto-dismiss
  }

  close(): void {
    this._clearTimer();
    this._toast.set(null);
  }

  private _clearTimer(): void {
    if (this._timer !== null) {
      clearTimeout(this._timer);
      this._timer = null;
    }
  }
}
```

**No `TranslateService` injection in `ToastService`** — callers pass pre-translated strings. Keeping the service lean.

### ToastContainerComponent TypeScript (T4.2)

```typescript
// src/app/shared/components/toast/toast-container.component.ts
import { Component, inject } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast-container',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './toast-container.component.html',
  styleUrl: './toast-container.component.scss',
})
export class ToastContainerComponent {
  readonly toastService = inject(ToastService);
}
```

### ToastContainerComponent HTML (T4.3)

```html
<!-- toast-container.component.html -->
@if (toastService.toast(); as toast) {
  <div
    class="toast"
    [class.toast--success]="toast.type === 'success'"
    [class.toast--error]="toast.type === 'error'"
    [attr.role]="toast.type === 'error' ? 'alert' : 'status'"
    [attr.aria-live]="toast.type === 'error' ? 'assertive' : 'polite'"
    aria-atomic="true">
    <span class="material-symbols-outlined toast__icon" aria-hidden="true">
      {{ toast.type === 'success' ? 'check_circle' : 'error' }}
    </span>
    <span class="toast__message">{{ toast.message }}</span>
    @if (toast.type === 'error') {
      <button
        type="button"
        class="toast__close"
        (click)="toastService.close()"
        [attr.aria-label]="'common.toast.close' | translate">
        <span class="material-symbols-outlined" aria-hidden="true">close</span>
      </button>
    }
  </div>
}
```

### ToastContainerComponent SCSS (T4.4)

```scss
// toast-container.component.scss
:host {
  // The host sits inside .app-shell but uses fixed positioning
  position: fixed;
  bottom: var(--pb-space-lg);
  right: var(--pb-space-lg);
  z-index: 200;
  pointer-events: none;
}

.toast {
  display: flex;
  align-items: center;
  gap: var(--pb-space-sm);
  padding: var(--pb-space-sm) var(--pb-space-md);
  border-radius: var(--pb-rounded-md);
  box-shadow: var(--pb-elevation-3);
  font-size: 14px;
  font-weight: 400;
  max-width: 400px;
  pointer-events: auto;   // re-enable on the toast itself

  &--success {
    background: var(--pb-success-container);   // #F0FDF4
    color: var(--pb-on-success-container);     // #166534
  }

  &--error {
    background: var(--mat-sys-error-container);
    color: var(--mat-sys-on-error-container);
  }

  &__icon {
    font-size: 18px;
    flex-shrink: 0;
  }

  &__message {
    flex: 1;
  }

  &__close {
    background: transparent;
    border: none;
    cursor: pointer;
    padding: 4px;
    color: inherit;
    display: flex;
    align-items: center;
    border-radius: var(--pb-rounded-sm);

    &:focus-visible {
      outline: 2px solid currentColor;
      outline-offset: 2px;
    }
  }
}
```

### AppLayoutComponent update (T4.5, T4.6)

`app-layout.component.ts` — add to imports array:
```typescript
import { ToastContainerComponent } from '../../shared/components/toast/toast-container.component';
// imports: [..., ToastContainerComponent]
```

`app-layout.component.html` — add as last child of `.app-shell` div, after `<main class="content">`:
```html
<app-toast-container />
```

**Existing spec regression check**: adding `ToastContainerComponent` to `AppLayoutComponent` imports may require providing `ToastService` in the spec. Since `ToastService` is `providedIn: 'root'` and has no external deps, `TestBed` will auto-create it. `ToastContainerComponent` uses no Angular Material animated components, so `provideAnimationsAsync()` is not needed. No spec changes expected, but run the suite after T4.5/T4.6 to confirm 56 tests still pass.

### SkeletonRowComponent (T5)

```typescript
// skeleton-row.component.ts
import { Component, input } from '@angular/core';

@Component({
  selector: 'app-skeleton-row',
  standalone: true,
  imports: [],
  templateUrl: './skeleton-row.component.html',
  styleUrl: './skeleton-row.component.scss',
})
export class SkeletonRowComponent {
  readonly rows = input<number>(3);
  readonly rowsArray = computed(() => Array(this.rows()).fill(0));
}
```

Add `computed` import from `@angular/core`.

```html
<!-- skeleton-row.component.html -->
<div class="skeleton-list" aria-hidden="true">
  @for (row of rowsArray(); track $index) {
    <div class="skeleton-row"></div>
  }
</div>
```

Note `aria-hidden="true"` on the container — screen readers should not announce skeleton rows.

```scss
// skeleton-row.component.scss
.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: var(--pb-space-xs);
}

.skeleton-row {
  height: 48px;
  background: var(--mat-sys-surface-variant);
  border-radius: var(--pb-rounded-md);
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

@keyframes skeleton-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
```

**Usage pattern**:
```html
<!-- While loading -->
@if (isLoading()) {
  <app-skeleton-row [rows]="5" />
} @else {
  <!-- actual list -->
}
```

### EmptyStateComponent (T6)

```typescript
// empty-state.component.ts
import { Component, input, output } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [TranslatePipe],
  templateUrl: './empty-state.component.html',
  styleUrl: './empty-state.component.scss',
})
export class EmptyStateComponent {
  readonly icon = input.required<string>();     // Material Symbols name, e.g. 'group'
  readonly message = input.required<string>(); // pre-translated message string
  readonly actionLabel = input<string | undefined>(undefined); // pre-translated, undefined = no button
  readonly action = output<void>();
}
```

```html
<!-- empty-state.component.html -->
<div class="empty-state">
  <span class="material-symbols-outlined empty-state__icon" aria-hidden="true">{{ icon() }}</span>
  <p class="empty-state__message">{{ message() }}</p>
  @if (actionLabel()) {
    <button type="button" class="btn-primary" (click)="action.emit()">
      {{ actionLabel() }}
    </button>
  }
</div>
```

```scss
// empty-state.component.scss
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--pb-space-md);
  padding: var(--pb-space-3xl) var(--pb-space-md);
  text-align: center;

  &__icon {
    font-size: 48px;
    color: var(--mat-sys-on-surface-variant);
    opacity: 0.6;
  }

  &__message {
    margin: 0;
    font-size: 14px;
    color: var(--mat-sys-on-surface-variant);
    max-width: 320px;
  }
}
```

### i18n Keys (T7)

**Add to `en.json`** — update the existing `"common"` key:
```json
"common": {
  "loading": "Loading...",
  "dialog": {
    "cancel": "Cancel",
    "confirm": "Confirm"
  },
  "toast": {
    "close": "Close"
  },
  "empty": {
    "clearFilters": "Clear filters"
  }
}
```

**Add to `fr.json`** — update the existing `"common"` key:
```json
"common": {
  "loading": "Chargement...",
  "dialog": {
    "cancel": "Annuler",
    "confirm": "Confirmer"
  },
  "toast": {
    "close": "Fermer"
  },
  "empty": {
    "clearFilters": "Effacer les filtres"
  }
}
```

**Both files already have `"common": { "loading": "..." }`** — merge these keys INTO the existing `"common"` object, do NOT replace or duplicate it.

### Spec Patterns (T8)

Follow all confirmed patterns from Story 1.7:
- `vi.fn()`, `signal()` — NEVER `jest.fn()` or `jasmine.createSpyObj()`
- `provideTranslateService({ lang: 'en' })` (NOT `TranslateModule.forRoot()`)
- `provideRouter([])` if component imports `RouterLink`/`RouterLinkActive`/`RouterOutlet`
- `provideAnimationsAsync()` if component uses Angular Material animated components

#### ConfirmDialogComponent spec

The CDK `Dialog` environment requires providing CDK dependencies. Use the `DialogRef` and `DIALOG_DATA` injection tokens directly in TestBed:

```typescript
import { signal } from '@angular/core';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { vi } from 'vitest';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

const testData: ConfirmDialogData = {
  title: 'Confirm action',
  description: 'This cannot be undone.',
};

describe('ConfirmDialogComponent', () => {
  const mockClose = vi.fn();
  const mockDialogRef = { close: mockClose };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: mockDialogRef },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('renders title and description', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement;
    expect(el.querySelector('.dialog__title').textContent).toContain('Confirm action');
    expect(el.querySelector('.dialog__description').textContent).toContain('This cannot be undone.');
  });

  it('confirm() calls dialogRef.close(true)', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith(true);
  });

  it('cancel() calls dialogRef.close(false)', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(false);
  });
});
```

#### ToastService spec

```typescript
import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
    vi.useFakeTimers();
  });

  afterEach(() => vi.useRealTimers());

  it('showSuccess sets toast with type success', () => {
    service.showSuccess('Saved!');
    expect(service.toast()).toEqual({ message: 'Saved!', type: 'success' });
  });

  it('showSuccess auto-dismisses after 4s', () => {
    service.showSuccess('Saved!');
    vi.advanceTimersByTime(4000);
    expect(service.toast()).toBeNull();
  });

  it('showError sets toast with type error and does not auto-dismiss', () => {
    service.showError('Printer offline');
    vi.advanceTimersByTime(10000);
    expect(service.toast()).toEqual({ message: 'Printer offline', type: 'error' });
  });

  it('close() clears toast immediately', () => {
    service.showError('Printer offline');
    service.close();
    expect(service.toast()).toBeNull();
  });

  it('showSuccess replaces previous toast', () => {
    service.showError('Error 1');
    service.showSuccess('Success');
    expect(service.toast()?.type).toBe('success');
  });
});
```

### Auth page SCSS (T9.1 / T9.4)

Use the same SCSS for both `login.component.scss` and `change-password.component.scss`:

```scss
:host {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.auth-card {
  background: var(--mat-sys-surface);
  border-radius: var(--pb-rounded-xl);
  box-shadow: var(--pb-elevation-2);
  padding: var(--pb-space-xl);
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: var(--pb-space-md);

  &__title {
    margin: 0;
    font-size: 22px;
    font-weight: 600;
    color: var(--mat-sys-on-surface);
  }

  &__description {
    margin: 0;
    font-size: 14px;
    color: var(--mat-sys-on-surface-variant);
    line-height: 1.5;
  }

  &__error {
    margin: 0;
    font-size: 13px;
    color: var(--mat-sys-error);
  }

  mat-form-field {
    width: 100%;
  }
}
```

`:host { min-height: 100vh }` stretches the component to fill the viewport. Since `/login` and `/change-password` are outside the `AppLayoutComponent` shell, their host element is a direct child of `<body>` — `min-height: 100vh` works without conflicts.

### LoginComponent HTML (T9.3)

```html
<form class="auth-card" [formGroup]="form" (ngSubmit)="onSubmit()">
  <h1 class="auth-card__title">{{ 'auth.login.title' | translate }}</h1>
  <mat-form-field appearance="outline">
    <mat-label>{{ 'auth.login.username' | translate }}</mat-label>
    <input matInput formControlName="username" type="text" autocomplete="username" />
  </mat-form-field>
  <mat-form-field appearance="outline">
    <mat-label>{{ 'auth.login.password' | translate }}</mat-label>
    <input matInput formControlName="password" type="password" autocomplete="current-password" />
  </mat-form-field>
  @if (error()) {
    <p class="auth-card__error" role="alert">{{ 'auth.login.error.' + error() | translate }}</p>
  }
  <button type="submit" class="btn-primary" [disabled]="form.invalid || loading()">
    {{ 'auth.login.submit' | translate }}
  </button>
</form>
```

### ChangePasswordComponent HTML (T9.6)

```html
<form class="auth-card" [formGroup]="form" (ngSubmit)="onSubmit()">
  <h1 class="auth-card__title">{{ 'auth.changePassword.title' | translate }}</h1>
  <p class="auth-card__description">{{ 'auth.changePassword.description' | translate }}</p>
  <mat-form-field appearance="outline">
    <mat-label>{{ 'auth.changePassword.newPassword' | translate }}</mat-label>
    <input matInput formControlName="newPassword" type="password" autocomplete="new-password" />
    <mat-error>{{ 'auth.changePassword.minLength' | translate }}</mat-error>
  </mat-form-field>
  @if (error()) {
    <p class="auth-card__error" role="alert">{{ 'auth.changePassword.error' | translate }}</p>
  }
  <button type="submit" class="btn-primary" [disabled]="form.invalid || loading()">
    {{ 'auth.changePassword.submit' | translate }}
  </button>
</form>
```

`<mat-error>` is shown automatically by Angular Material when the control is invalid AND touched — the explicit `@if` wrapper from the original template is no longer needed.

### TypeScript updates for LoginComponent and ChangePasswordComponent (T9.2 / T9.5)

Add to `imports` array in both components:
```typescript
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
// imports: [..., MatFormFieldModule, MatInputModule]
```

Add `styleUrl` to the `@Component` decorator:
```typescript
styleUrl: './login.component.scss'      // login
styleUrl: './change-password.component.scss'   // change-password
```

No other TypeScript changes — form logic and signal state are untouched.

### Auth spec patterns (T8.9 / T8.10)

Add `provideAnimationsAsync()` because `MatFormField` uses animations.

```typescript
// login.component.spec.ts
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { AuthService } from '../../../services/auth.service';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  const mockAuth = {
    login: vi.fn(),
    currentUser: signal(null),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: mockAuth },
      ],
    }).compileComponents();
  });

  it('renders username and password fields', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement;
    expect(el.querySelector('input[autocomplete="username"]')).not.toBeNull();
    expect(el.querySelector('input[autocomplete="current-password"]')).not.toBeNull();
  });

  it('submit button is disabled when form is empty', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.disabled).toBe(true);
  });

  it('shows error alert when error signal is set', () => {
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();
    fixture.componentInstance.error.set('invalid-credentials');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
```

```typescript
// change-password.component.spec.ts
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { AuthService } from '../../../services/auth.service';
import { ChangePasswordComponent } from './change-password.component';

describe('ChangePasswordComponent', () => {
  const mockAuth = {
    changePassword: vi.fn(),
    currentUser: signal(null),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ChangePasswordComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: mockAuth },
      ],
    }).compileComponents();
  });

  it('renders newPassword field', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[autocomplete="new-password"]')).not.toBeNull();
  });

  it('submit button is disabled when form is empty', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.disabled).toBe(true);
  });

  it('shows error alert when error signal is true', () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    fixture.detectChanges();
    fixture.componentInstance.error.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });
});
```

### Material Symbols — do NOT use `MatIcon`

**Same rule as Story 1.7**: `MatIcon` requires `MatIconRegistry` configuration and renders squares without it. Always use:
```html
<span class="material-symbols-outlined" aria-hidden="true">icon_name</span>
```

All new components use this pattern. Do NOT add `MatIcon` to any component `imports` array.

### What NOT to change

- `authGuard`, `adminGuard` — no changes
- Any backend file — this story is 100% frontend
- `LoginComponent` and `ChangePasswordComponent` TypeScript logic — only `styleUrl`, `MatFormFieldModule`, and `MatInputModule` are added; form logic and signal state are untouched
- `AdminSettingsComponent` and all other non-auth components — unchanged
- `app.ts` — stays `<router-outlet />`
- `admin.routes.ts` — unchanged
- The guard specs — no changes
- The existing `"common": { "loading": "..." }` value in both JSON files — MERGE keys, not replace
- The existing `--mat-sys-secondary` line in `styles.scss` must be UPDATED in place (not duplicated) — SCSS uses the last value when variables are declared twice in the same scope

### styles.scss context

Current state of `styles.scss` after Story 1.7 (verified from source):
- Angular Material M3 theme with `mat.theme()` (red palette)
- `:root` block with `--mat-sys-*` coral overrides, secondary overrides (approximate values from 1.7 review), and `--pb-*` custom tokens
- Global base styles (box-sizing, body font, background, color)
- Component-scoped focus rings exist in `app-layout.component.scss` but NOT in global `styles.scss`
- **NO `.btn-primary`, `.btn-error`, `.btn-ghost` global classes exist yet** — all buttons are either component-scoped or unstyled

**Secondary colors already present but with approximate values** — T1.1 must UPDATE the existing values, not add new lines. Find and replace:
- `--mat-sys-secondary: #8C5C4E` → `--mat-sys-secondary: #8C5C42`
- `--mat-sys-secondary-container: #F5EEEA` → `--mat-sys-secondary-container: #FFDCC5`
- `--mat-sys-on-secondary-container: #3D1A10` → `--mat-sys-on-secondary-container: #331200`

**T1.2–T1.6 additions go AFTER the existing `:root` block** — do not modify or replace existing theme content.

**`.btn-ghost` current location**: `app-layout.component.scss` (~lines 94–112). Styles to copy to `styles.scss` for T1.4:
```scss
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
}
```
After copying to global, T1.7 removes the block from `app-layout.component.scss` and removes the duplicate `&:focus-visible` overrides in `.btn-ghost` and `.sidebar__item`.

### CSS Design Token Reminder

- **Never hardcode `#C44626`** — always `var(--mat-sys-primary)`
- **Never hardcode `#FFFFFF`** — always `var(--mat-sys-on-primary)` on primary bg or `var(--mat-sys-on-error)` on error bg
- Use `var(--pb-elevation-3)` for dialogs
- Use `var(--pb-rounded-xl)` (20px) for dialogs, `var(--pb-rounded-md)` (8px) for buttons/inputs

## Project Structure Notes

**New files:**
```
pluribourse-frontend/src/app/features/auth/login/login.component.scss
pluribourse-frontend/src/app/features/auth/login/login.component.spec.ts
pluribourse-frontend/src/app/features/auth/change-password/change-password.component.scss
pluribourse-frontend/src/app/features/auth/change-password/change-password.component.spec.ts
pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts
pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.html
pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.scss
pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.spec.ts
pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts
pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.spec.ts
pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.ts
pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.html
pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.scss
pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.spec.ts
pluribourse-frontend/src/app/shared/components/toast/toast.service.ts
pluribourse-frontend/src/app/shared/components/toast/toast.service.spec.ts
pluribourse-frontend/src/app/shared/components/toast/toast-container.component.ts
pluribourse-frontend/src/app/shared/components/toast/toast-container.component.html
pluribourse-frontend/src/app/shared/components/toast/toast-container.component.scss
pluribourse-frontend/src/app/shared/components/toast/toast-container.component.spec.ts
pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.ts
pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.html
pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.scss
pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.spec.ts
pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.ts
pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.html
pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.scss
pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.spec.ts
```

**Modified files:**
```
pluribourse-frontend/src/styles.scss                                         ← accessibility baseline + deferred fixes
pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts      ← import ToastContainerComponent
pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html    ← <app-toast-container /> at end
pluribourse-frontend/public/i18n/en.json                                     ← merge common.dialog/toast/empty keys
pluribourse-frontend/public/i18n/fr.json                                     ← merge common.dialog/toast/empty keys
pluribourse-frontend/src/app/features/auth/login/login.component.ts         ← styleUrl + MatFormFieldModule + MatInputModule
pluribourse-frontend/src/app/features/auth/login/login.component.html       ← mat-form-field + btn-primary
pluribourse-frontend/src/app/features/auth/change-password/change-password.component.ts   ← styleUrl + MatFormFieldModule + MatInputModule
pluribourse-frontend/src/app/features/auth/change-password/change-password.component.html ← mat-form-field + btn-primary
```

**Directory structure alignment**: architecture.md shows `shared/` under `app/` with a `components/` folder. Story 1.7 created `src/app/layout/` and `src/app/shared/components/` exists but is empty. All new shared components go under `src/app/shared/components/[component-name]/`.

## References

- [Source: epics.md#Story 1.8] — user story, acceptance criteria (line 557–600)
- [Source: epics.md#Epic 1 UX requirements] — UX-DR6, UX-DR7, UX-DR8, UX-DR12, UX-DR13, UX-DR20 (lines 186–200)
- [Source: ux-designs/DESIGN.md#Components] — dialog, notification-inline, toast, skeleton-row, empty state visual specs
- [Source: ux-designs/EXPERIENCE.md#Component Patterns] — dialog/toast/notification behavioral rules (lines 136–160)
- [Source: architecture.md#Frontend structure] — `shared/components/confirm-dialog.component.ts` location (line 713)
- [Source: 1-7-systeme-de-design-angular-material-mise-en-page-applicative.md] — confirmed Vitest patterns, design tokens, Material Symbols pattern, deferred issues
- [Source: styles.scss] — current state: existing `:root` overrides to add secondary colors after
- [Source: app-layout.component.html] — current state: add `<app-toast-container />` as last child of `.app-shell`
- [Source: en.json, fr.json] — current state: `"common": { "loading": "..." }` to be extended

## Dev Agent Record

### Agent Model Used
claude-sonnet-4-6

### Debug Log References

- **auth.service.spec.ts — 3 pre-existing failures (not caused by this story)**: `changePassword` tests flush `null` from the server but the service does `this.currentUser.set(updated)` where `updated = null`, making `currentUser()` null. Tests expect local mutation of `forcePasswordChange`. The `restoreSession` 403 test expects original user to be preserved, but implementation sets a sentinel. These failures exist at baseline commit `72e9b40` — not touched by story 1.8.
- **EmptyStateComponent `TranslatePipe` unused warning**: Callers pass pre-translated strings; `TranslatePipe` was removed from component imports (message/actionLabel are ready-translated inputs, not keys).

### Completion Notes List

- T1: Updated exact UX secondary color values, added `.btn-primary`, `.btn-error`, `.btn-ghost` global classes, WCAG 2.2 focus ring baseline, `.sr-only` utility, CDK dialog backdrop. Removed now-redundant component-scoped `.btn-ghost` and focus-visible rules from `app-layout.component.scss`.
- T2: Created `ConfirmDialogComponent` (CDK Dialog, `A11yModule` for `cdkFocusInitial`, focus trap automatic), `ConfirmDialogService` (opens with backdrop, Escape key, returns `ref.closed` Observable).
- T3: Created `NotificationInlineComponent` with warning/error variants, correct `aria-live` polite/assertive, `aria-hidden` icon.
- T4: Created `ToastService` (signal-based, 4s auto-dismiss for success, persistent error), `ToastContainerComponent` (fixed positioning via `:host`, role=alert/status). Updated `AppLayoutComponent` to render `<app-toast-container />`.
- T5: Created `SkeletonRowComponent` with `computed()` rows array, `aria-hidden="true"` container, CSS pulse animation.
- T6: Created `EmptyStateComponent` (icon, message, optional action button). Removed unused `TranslatePipe` — inputs are pre-translated.
- T7: Merged `common.dialog`, `common.toast`, `common.empty` keys into existing `"common"` object in both `en.json` and `fr.json`.
- T8: 42 new tests across 9 spec files, all passing. 96/99 total tests pass — 3 failures are pre-existing in `auth.service.spec.ts` (see debug log).
- T9: Created login/change-password SCSS with centered card layout, updated HTML to use `mat-form-field` + `matInput` + `.btn-primary`.

### File List

**New files:**
- pluribourse-frontend/src/app/features/auth/login/login.component.scss
- pluribourse-frontend/src/app/features/auth/login/login.component.spec.ts
- pluribourse-frontend/src/app/features/auth/change-password/change-password.component.scss
- pluribourse-frontend/src/app/features/auth/change-password/change-password.component.spec.ts
- pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts
- pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.html
- pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.scss
- pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.spec.ts
- pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts
- pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.spec.ts
- pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.ts
- pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.html
- pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.scss
- pluribourse-frontend/src/app/shared/components/notification-inline/notification-inline.component.spec.ts
- pluribourse-frontend/src/app/shared/components/toast/toast.service.ts
- pluribourse-frontend/src/app/shared/components/toast/toast.service.spec.ts
- pluribourse-frontend/src/app/shared/components/toast/toast-container.component.ts
- pluribourse-frontend/src/app/shared/components/toast/toast-container.component.html
- pluribourse-frontend/src/app/shared/components/toast/toast-container.component.scss
- pluribourse-frontend/src/app/shared/components/toast/toast-container.component.spec.ts
- pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.ts
- pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.html
- pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.scss
- pluribourse-frontend/src/app/shared/components/skeleton-row/skeleton-row.component.spec.ts
- pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.ts
- pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.html
- pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.scss
- pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.spec.ts

**Modified files:**
- pluribourse-frontend/src/styles.scss
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html
- pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss
- pluribourse-frontend/public/i18n/en.json
- pluribourse-frontend/public/i18n/fr.json
- pluribourse-frontend/src/app/features/auth/login/login.component.ts
- pluribourse-frontend/src/app/features/auth/login/login.component.html
- pluribourse-frontend/src/app/features/auth/change-password/change-password.component.ts
- pluribourse-frontend/src/app/features/auth/change-password/change-password.component.html
