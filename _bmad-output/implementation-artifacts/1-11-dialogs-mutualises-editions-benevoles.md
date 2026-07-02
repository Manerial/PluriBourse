---
baseline_commit: c901ba0091e7c7e2b0f4fe7155a1205cc00f5e6f
---

# Story 1.11: Shared Dialogs for Edition and Volunteer Management

Status: done

## Story

As an administrator,
I want to create/edit an edition, manage its phases, manage its categories, and add a volunteer from dialog boxes instead of dedicated pages,
so that I stay in the context of the current list and get a consistent closing experience (X button, Cancel, Escape) across all these short admin actions.

## Acceptance Criteria

1. **Given** the admin is on `/admin/editions`, **When** they click "Créer une édition", **Then** a dialog opens (via `DialogShellComponent`) with the edition creation form, with no URL change.

2. **Given** the admin clicks "Modifier" on an edition row, **When** the dialog opens, **Then** the form is pre-filled with the edition's data and the dialog title shows the edition's name.

3. **Given** the admin clicks "Gérer les phases" on an edition row, **When** the dialog opens, **Then** the phase control content (current phase, advance/rollback buttons) is displayed in the dialog **And** the existing nested confirmation dialogs (advance/rollback) still work stacked on top of it.

4. **Given** the admin clicks "Gérer les catégories" on an edition row, **When** the dialog opens, **Then** the categories table (add/remove rows) is displayed in the dialog **And** the dialog body scrolls vertically if content exceeds viewport height, while the title and close button stay fixed.

5. **Given** the admin is on `/admin/users`, **When** they click "Créer un utilisateur", **Then** a dialog opens with the volunteer creation form, with no URL change.

6. **Given** any of these 4 dialogs (or the 2 existing dialogs — confirmation, password reset) is open, **When** the admin clicks the close (X) button top-right, clicks Cancel, or presses Escape, **Then** the dialog closes without performing the action **And** focus returns to the element that triggered it.

7. **Given** the routes `editions/create`, `editions/:id/edit`, `editions/:id/phase`, `editions/:id/categories`, and `users/create` previously existed in `admin.routes.ts`, **When** this story is complete, **Then** these routes no longer exist — the corresponding features are only reachable via dialog from the parent list.

8. **Given** `ConfirmDialogComponent` and `ResetPasswordDialogComponent` already exist (Stories 1.8, 1.10), **When** this story is complete, **Then** both use `DialogShellComponent` as their shared container, with a close button functionally equivalent to Cancel/Escape.

## Tasks / Subtasks

- [x] **T1 — Create `DialogShellComponent`** (AC: 6, 8) — shared, standalone, reusable container for every dialog in the app, present and future
  - [x] T1.1 — Create `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.ts` (see Dev Notes)
  - [x] T1.2 — Create `dialog-shell.component.html` (see Dev Notes)
  - [x] T1.3 — Create `dialog-shell.component.scss` (see Dev Notes — this is now the SINGLE source of truth for dialog chrome: background, radius, shadow, width, scroll)
  - [x] T1.4 — Create `dialog-shell.component.spec.ts` using a host test component (see Dev Notes)
  - [x] T1.5 — Add `common.dialog.close` i18n key to `en.json` / `fr.json` (see Dev Notes)

- [x] **T2 — Migrate `ConfirmDialogComponent` onto `DialogShellComponent`** (AC: 8)
  - [x] T2.1 — Update `confirm-dialog.component.html`: wrap content in `<app-dialog-shell>` (see Dev Notes)
  - [x] T2.2 — Update `confirm-dialog.component.ts`: add `DialogShellComponent` to `imports`
  - [x] T2.3 — Delete `confirm-dialog.component.scss`, remove `styleUrl` from `@Component` — remaining rules move to global `styles.scss` (see Dev Notes)
  - [x] T2.4 — Update `confirm-dialog.service.ts`: replace `ariaLabelledBy: 'dialog-title'` with `ariaLabel: data.title` (see Dev Notes)
  - [x] T2.5 — Update `confirm-dialog.component.spec.ts` and `confirm-dialog.service.spec.ts` (see Dev Notes)

- [x] **T3 — Migrate `ResetPasswordDialogComponent` onto `DialogShellComponent`** (AC: 8)
  - [x] T3.1 — Update `reset-password-dialog.component.html`: wrap content in `<app-dialog-shell>` (see Dev Notes)
  - [x] T3.2 — Update `reset-password-dialog.component.ts`: add `DialogShellComponent` to `imports`
  - [x] T3.3 — Delete `reset-password-dialog.component.scss`, remove `styleUrl`
  - [x] T3.4 — Update `user-list.component.ts` `openResetPasswordDialog()`: replace `ariaLabelledBy: 'reset-dialog-title'` with `ariaLabel: this.translate.instant('admin.users.resetDialog.title')`
  - [x] T3.5 — Update `reset-password-dialog.component.spec.ts`

- [x] **T4 — Convert `EditionFormComponent` (create + edit) to dialog content** (AC: 1, 2, 6)
  - [x] T4.1 — Update `edition-form.component.ts`: replace `ActivatedRoute`/`Router`/`RouterLink` with `DIALOG_DATA`/`DialogRef`, add `EditionFormDialogData` interface, `dialogTitle` computed, `loadedEditionName` signal (see Dev Notes)
  - [x] T4.2 — Update `edition-form.component.html`: replace the `<div class="card form-card">` wrapper with `<app-dialog-shell>` (see Dev Notes)
  - [x] T4.3 — Update `edition-form.component.spec.ts`: replace router/ActivatedRoute mocking with `DIALOG_DATA`/`DialogRef` mocking, add create-mode and edit-mode test groups (see Dev Notes)

- [x] **T5 — Convert `PhaseControlComponent` to dialog content** (AC: 3, 6)
  - [x] T5.1 — Update `phase-control.component.ts`: replace `ActivatedRoute`/`RouterLink` with `DIALOG_DATA`/`DialogRef`, add `PhaseControlDialogData` interface (see Dev Notes)
  - [x] T5.2 — Update `phase-control.component.html`: remove the manual `<div class="card"><div class="card__header">` wrapper (back-arrow + title, added this session) and replace with `<app-dialog-shell [title]="...">` (see Dev Notes)
  - [x] T5.3 — Update `phase-control.component.spec.ts`: replace `ActivatedRoute` mock with `DIALOG_DATA` mock (see Dev Notes)

- [x] **T6 — Convert `EditionCategoriesComponent` to dialog content** (AC: 4, 6)
  - [x] T6.1 — Update `edition-categories.component.ts`: replace `ActivatedRoute`/`RouterLink` with `DIALOG_DATA`/`DialogRef`, add `EditionCategoriesDialogData` interface, drop the now-unneeded `rawId`/`isNaN` guard (caller always supplies a valid id) (see Dev Notes)
  - [x] T6.2 — Update `edition-categories.component.html`: remove the manual header block (`card__header-title`, back-arrow, added this session) and replace with `<app-dialog-shell [title]="...">` (see Dev Notes)
  - [x] T6.3 — Update `edition-categories.component.spec.ts`: replace `ActivatedRoute` mock with `DIALOG_DATA` mock (see Dev Notes)

- [x] **T7 — Convert `UserFormComponent` (create) to dialog content** (AC: 5, 6)
  - [x] T7.1 — Update `user-form.component.ts`: replace `Router` with `DialogRef` (no `DIALOG_DATA` needed — create-only) (see Dev Notes)
  - [x] T7.2 — Update `user-form.component.html`: replace the `<div class="card form-card">` wrapper with `<app-dialog-shell>` (see Dev Notes)
  - [x] T7.3 — Update `user-form.component.spec.ts`: replace router mocking with `DialogRef` mocking (see Dev Notes)

- [x] **T8 — `EditionListComponent`: wire dialog triggers, remove page navigation** (AC: 1, 2, 3, 4)
  - [x] T8.1 — Update `edition-list.component.ts`: extract `loadEditions()`, inject `Dialog`, add `openCreateDialog()`, `openEditDialog()`, `openPhaseDialog()`, `openCategoriesDialog()`; remove `navigateToCreate()`, `navigateToEdit()`, `Router` (see Dev Notes)
  - [x] T8.2 — Update `edition-list.component.html`: replace `routerLink` create button and per-row `routerLink`/`(click)="navigateToEdit(...)"` actions with `(click)` dialog triggers; remove `RouterLink` from `imports` (see Dev Notes)
  - [x] T8.3 — Update `edition-list.component.spec.ts`: replace `navigateToEdit` router-spy test with dialog-open tests, add `Dialog` mock (see Dev Notes)

- [x] **T9 — `UserListComponent`: wire create dialog trigger, remove page navigation** (AC: 5)
  - [x] T9.1 — Update `user-list.component.ts`: extract `loadUsers()`, add `openCreateDialog()`, remove `navigateToCreate()`, `Router` (see Dev Notes)
  - [x] T9.2 — Update `user-list.component.html`: replace `routerLink="create"` button with `(click)="openCreateDialog()"`; remove `RouterLink` from `imports`
  - [x] T9.3 — Update `user-list.component.spec.ts`: replace router-based create test with dialog-open test (see Dev Notes)

- [x] **T10 — Remove obsolete routes** (AC: 7)
  - [x] T10.1 — Update `admin.routes.ts`: remove `users/create`, `editions/create`, `editions/:id/edit`, `editions/:id/phase`, `editions/:id/categories` (see Dev Notes)

- [x] **T11 — i18n cleanup**
  - [x] T11.1 — Remove now-dead keys from `en.json`/`fr.json`: `category.back`, `phase.control.back`, `edition.create.backToList` — grep the codebase first to confirm no remaining references before deleting (see Dev Notes)

- [x] **T12 — CSS cleanup in `styles.scss`**
  - [x] T12.1 — Remove `.card__header-title` and `.card--narrow` rules — both were introduced earlier this session specifically for the page-based `EditionCategoriesComponent`/`PhaseControlComponent` headers; neither component renders a `.card` anymore after T5/T6, so both selectors become dead (see Dev Notes)
  - [x] T12.2 — Remove the `.card--narrow .data-table th:first-child / td:first-child / th:last-child / td:last-child` rule — superseded by `DialogShellComponent`'s own body padding (`--pb-space-lg` on all sides), which now solves the same "table too close to the edge" problem generically (see Dev Notes)
  - [x] T12.3 — Add shared `.dialog__description`, `.dialog__desc`, `.dialog__field`, `.dialog__actions`, `.dialog-form` utility classes (consolidated from the deleted per-component scss files in T2/T3, reused by T4–T7) (see Dev Notes)

- [x] **T13 — Run `npm test`** — all existing tests must pass, zero regressions, coverage stays ≥ 80%

### Review Findings

- [x] [Review][Patch] `translate.instant()` inside `computed()` signals is not reactive to a runtime language switch [pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts:59-61, pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts:48-50] — fixed via a `langChange = toSignal(translate.onLangChange, ...)` signal read inside each `computed()` to force recomputation on language change.
- [x] [Review][Patch] `openCategoriesDialog` never refreshes the edition list after save [pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts:104-116] — fixed: wired `.closed.pipe(takeUntilDestroyed(...)).subscribe(() => this.loadEditions())`, matching `openEditionDialog`/`openPhaseDialog`.
- [x] [Review][Patch] `ariaLabel` doesn't match the dialog's visible title in 3 places [pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts:68,98,113] — fixed: `openEditionDialog` now takes an explicit `ariaLabel` param (edition's real name in edit mode); phase/categories dialogs build `ariaLabel` via `translate.instant('phase.control.title'/'category.title', { editionName })`, matching the title actually rendered.
- [x] [Review][Patch] `lockedPhaseLabel` tests assert on the untranslated i18n key, not the actual translation [pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.spec.ts:96-105] — fixed: test now loads a real `en` translation fixture via `TranslateService.setTranslation` and asserts on the translated string.
- [x] [Review][Patch] `editionId` validation guard dropped in `ngOnInit` [pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts:68-70] — fixed: restored a guard (`!editionId || editionId <= 0`) setting `category.load.error` and returning before calling the API.
- [x] [Review][Patch] Same missing `editionId` validation guard [pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts:44-52] — fixed: same guard pattern added, setting `phase.control.error.load`.
- [x] [Review][Patch] `currentEditionService.loadEdition().subscribe()` has no error handler at 2 call sites [pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts:73,138] — fixed: both now subscribe with `{ error: () => {} }` to swallow the error instead of letting it surface unhandled.
- [x] [Review][Patch] Dialog title renders blank while loading in edit mode [pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts:48-50] — fixed: `dialogTitle` now falls back to `translate.instant('edition.edit.title')` while `loadedEditionName()` is still `null`.
- [x] [Review][Patch] Async-refresh tests don't exercise real async timing [pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts:31,37] — fixed: `dialogMock.open`'s `closed` observable now emits via `from(Promise.resolve(undefined))` (microtask-deferred) instead of synchronous `of(undefined)`; also added a regression test for the categories-dialog refresh fix above.

**Verification:** `npm test` (pluribourse-frontend) — 39 test files, 287 tests, all passing after all patches above.

## Dev Notes

### Continuity note — this story supersedes earlier session changes

Earlier in this same working session (before this story existed), `PhaseControlComponent` and `EditionCategoriesComponent` received page-layout CSS fixes: a manual back-arrow icon-button + title grouped in `.card__header-title`, and a `.card--narrow` modifier to constrain page width. **This story replaces that approach entirely** — both components stop rendering their own `.card`/`.card__header` and become dialog content wrapped in `DialogShellComponent`, which owns the title, the close affordance, and the width constraint (640px, matching what `.card--narrow` used). T5, T6, and T12 explicitly remove the now-dead markup/CSS from that earlier work. Do not treat this as a regression — it is intentional supersession within the same UX evolution.

### T1 — `DialogShellComponent`

Location: `pluribourse-frontend/src/app/shared/components/dialog-shell/` (shared utility, like `empty-state`, `notification-inline`, `skeleton-row` in the same folder).

Follow the existing signal-based `input()`/`output()` convention used by `EmptyStateComponent` / `NotificationInlineComponent` in this same folder — **not** `@Input()`/`@Output()` decorators.

```typescript
// dialog-shell.component.ts
import { Component, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-dialog-shell',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, TranslatePipe],
  templateUrl: './dialog-shell.component.html',
  styleUrl: './dialog-shell.component.scss',
})
export class DialogShellComponent {
  readonly title = input.required<string>();
  readonly close = output<void>();
}
```

```html
<!-- dialog-shell.component.html -->
<div class="dialog-shell">
  <div class="dialog-shell__header">
    <h2 class="dialog-shell__title">{{ title() }}</h2>
    <button
      type="button"
      mat-icon-button
      class="dialog-shell__close"
      [attr.aria-label]="'common.dialog.close' | translate"
      (click)="close.emit()">
      <mat-icon>close</mat-icon>
    </button>
  </div>
  <div class="dialog-shell__body">
    <ng-content />
  </div>
</div>
```

```scss
// dialog-shell.component.scss — the single source of truth for dialog chrome
.dialog-shell {
  display: flex;
  flex-direction: column;
  background: var(--mat-sys-surface);
  border-radius: var(--pb-rounded-xl);
  box-shadow: var(--pb-elevation-3);
  min-width: 360px;
  max-width: 640px; // matches DESIGN.md components.dialog.max-width
  width: 100%;
  max-height: calc(100vh - var(--pb-space-2xl));
  outline: none;

  &__header {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: var(--pb-space-md);
    padding: var(--pb-space-lg) var(--pb-space-lg) 0;
    flex-shrink: 0;
  }

  &__title {
    margin: 0;
    font-size: 18px;
    font-weight: 600;
    color: var(--mat-sys-on-surface);
  }

  &__close {
    flex-shrink: 0;
    margin: -8px -8px 0 0; // optically align the 44px hit-target with the header edge
  }

  &__body {
    padding: var(--pb-space-lg);
    overflow-y: auto;
  }
}
```

**Why no `role`/`aria-labelledby` wiring inside this component:** the accessible name is supplied by the caller via `DialogConfig.ariaLabel` (a **literal string**, not an ID reference) when calling `Dialog.open(...)`. This sidesteps a real problem: if `DialogShellComponent` generated its own DOM `id` for the `<h2>` and callers referenced it via `ariaLabelledBy`, two simultaneously-open dialogs (e.g. the nested confirm-dialog stacked on top of the phase-control dialog, AC 3) would either collide on a hardcoded id or require complex unique-id plumbing across a `ng-content` boundary. `DialogConfig.ariaLabel?: string | null` exists precisely for this (see `@angular/cdk/types/dialog.d.ts`) and every caller already knows the title as a plain string at `.open()` time. Do not add `ariaLabelledBy` pointing into `DialogShellComponent`'s template anywhere in this story.

**Why no `cdkTrapFocus` here:** CDK `Dialog` already installs a focus trap on the whole `cdk-dialog-container` automatically (confirmed by the existing Story 1.10 dev notes on `ConfirmDialogComponent`). The close button is part of the projected DOM tree and is included in that trap for free. Do not add a second trap directive.

**Why no `cdkFocusInitial` on the close button:** each consumer already decides its own initial-focus target (cancel button for confirmations, first input for forms) via `cdkFocusInitial` on their own projected content. Leaving it off the shell avoids fighting that per-consumer choice.

Add i18n key under the existing `common.dialog` object (already has `cancel`/`confirm`):

`en.json`:
```json
"dialog": {
  "cancel": "Cancel",
  "confirm": "Confirm",
  "close": "Close"
}
```
`fr.json`:
```json
"dialog": {
  "cancel": "Annuler",
  "confirm": "Confirmer",
  "close": "Fermer"
}
```

**Test pattern (T1.4)** — `input.required` + `output` components are tested through a host wrapper, not by setting inputs directly on a bare fixture:

```typescript
// dialog-shell.component.spec.ts
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { provideTranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { DialogShellComponent } from './dialog-shell.component';

@Component({
  standalone: true,
  imports: [DialogShellComponent],
  template: `<app-dialog-shell title="Test title" (close)="onClose()"><p class="projected">content</p></app-dialog-shell>`,
})
class HostComponent {
  closed = false;
  onClose(): void { this.closed = true; }
}

describe('DialogShellComponent', () => {
  let fixture: ComponentFixture<HostComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HostComponent],
      providers: [provideTranslateService({ lang: 'en' })],
    }).compileComponents();
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
  });

  it('renders the title', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('.dialog-shell__title');
    expect(el.textContent).toContain('Test title');
  });

  it('projects content', () => {
    const el: HTMLElement = fixture.nativeElement.querySelector('.projected');
    expect(el.textContent).toBe('content');
  });

  it('emits close when the close button is clicked', () => {
    fixture.debugElement.query(By.css('.dialog-shell__close')).nativeElement.click();
    expect(fixture.componentInstance.closed).toBe(true);
  });
});
```

### T2 — `ConfirmDialogComponent` migration

```html
<!-- confirm-dialog.component.html -->
<app-dialog-shell [title]="data.title" (close)="cancel()">
  <p id="dialog-desc" class="dialog__description">{{ data.description }}</p>
  <div class="dialog__actions">
    <button type="button" mat-button cdkFocusInitial (click)="cancel()">
      {{ data.cancelLabel ?? ('common.dialog.cancel' | translate) }}
    </button>
    <button
      type="button"
      mat-flat-button
      [color]="data.confirmVariant === 'error' ? 'warn' : 'primary'"
      (click)="confirm()">
      {{ data.confirmLabel ?? ('common.dialog.confirm' | translate) }}
    </button>
  </div>
</app-dialog-shell>
```

`confirm-dialog.component.ts`: add `DialogShellComponent` to `imports`. Keep `A11yModule` (still needed for `cdkFocusInitial`). No other logic changes — `cancel()`/`confirm()` already exist and are unchanged.

Delete `confirm-dialog.component.scss` entirely and remove `styleUrl: './confirm-dialog.component.scss'` from the `@Component` decorator (the component now relies purely on global `styles.scss` utility classes added in T12.3, plus `DialogShellComponent`'s own scss).

`confirm-dialog.service.ts` — swap the ARIA wiring:

```typescript
open(data: ConfirmDialogData): Observable<boolean | undefined> {
  const ref = this.dialog.open<boolean, ConfirmDialogData, ConfirmDialogComponent>(
    ConfirmDialogComponent,
    {
      data,
      hasBackdrop: true,
      backdropClass: 'dialog-backdrop',
      panelClass: 'dialog-panel',
      disableClose: false,
      ariaLabel: data.title,
      ariaDescribedBy: 'dialog-desc',
    }
  );
  return ref.closed;
}
```

`ariaDescribedBy: 'dialog-desc'` is unchanged and stays valid — that `<p id="dialog-desc">` is still owned by `ConfirmDialogComponent`'s own template (inside the shell's projected content), and only one `ConfirmDialogComponent` instance is ever open at a time in this app (no collision risk, unlike the title which the shell now owns generically).

**`confirm-dialog.component.spec.ts` — exact break to fix:** the test `'renders title and description'` queries `el.querySelector('.dialog__title')` (line 32) — this class no longer exists on `ConfirmDialogComponent`'s own template after migration (the `<h2>` now lives inside `DialogShellComponent`, class `.dialog-shell__title`). Angular renders `DialogShellComponent`'s full template inside `ConfirmDialogComponent`'s fixture (standalone child components render by default, no `NO_ERRORS_SCHEMA`/shallow rendering is in use here), so the fix is a one-line selector change:
```typescript
expect(el.querySelector('.dialog-shell__title').textContent).toContain('Confirm action');
expect(el.querySelector('.dialog__description').textContent).toContain('This cannot be undone.'); // unchanged, still ConfirmDialogComponent's own element
```
Add one new test:
```typescript
it('close button calls cancel()', () => {
  const fixture = TestBed.createComponent(ConfirmDialogComponent);
  fixture.detectChanges();
  fixture.nativeElement.querySelector('.dialog-shell__close').click();
  expect(mockClose).toHaveBeenCalledWith(false);
});
```
All other tests in this file (`confirm()`, `cancel()`, cdkFocusInitial, color classes) are unaffected — they query elements that stay in `ConfirmDialogComponent`'s own template.

**`confirm-dialog.service.spec.ts`:** no existing assertion touches `ariaLabelledBy`/`ariaLabel`, so nothing breaks from T2.4's change. Optionally add `expect(callArgs.ariaLabel).toBe('Test')` to the `'opens with backdrop enabled'` test for coverage of the new config field — not required to keep the suite green.

### T3 — `ResetPasswordDialogComponent` migration

Same pattern as T2:

```html
<!-- reset-password-dialog.component.html -->
<app-dialog-shell [title]="'admin.users.resetDialog.title' | translate" (close)="cancel()">
  <p id="reset-dialog-desc" class="dialog__desc">
    {{ 'admin.users.resetDialog.description' | translate: { name: data.userName } }}
  </p>
  <form [formGroup]="form" (ngSubmit)="confirm()">
    <mat-form-field appearance="outline" class="dialog__field">
      <mat-label>{{ 'admin.users.create.password' | translate }}</mat-label>
      <input matInput formControlName="newPassword" type="password" autocomplete="new-password" cdkFocusInitial />
      <mat-error>{{ 'auth.changePassword.minLength' | translate }}</mat-error>
    </mat-form-field>
    <div class="dialog__actions">
      <button type="button" mat-button (click)="cancel()">
        {{ 'admin.users.create.cancel' | translate }}
      </button>
      <button type="submit" mat-flat-button color="primary" [disabled]="form.invalid">
        {{ 'admin.users.actions.confirmReset' | translate }}
      </button>
    </div>
  </form>
</app-dialog-shell>
```

Add `DialogShellComponent` to `imports`. Delete `reset-password-dialog.component.scss`, remove `styleUrl`. No changes to `confirm()`/`cancel()` logic.

`user-list.component.ts` `openResetPasswordDialog()` — swap `ariaLabelledBy: 'reset-dialog-title'` for `ariaLabel: this.translate.instant('admin.users.resetDialog.title')` in the `Dialog.open(...)` config (this component already injects `TranslateService`).

**`reset-password-dialog.component.spec.ts` — exact break to fix:** the test `'renders title and user name in description'` (line 27) queries `el.querySelector('.dialog__title')` — same fix as T2, becomes `.dialog-shell__title`. `.dialog__desc` is unchanged (stays in `ResetPasswordDialogComponent`'s own template). All other tests (`confirm()`, `cancel()`, password-strength checks, submit-button disabled state) are unaffected. Add the same close-button test pattern as T2.5, using `mockClose` and `.dialog-shell__close`, asserting `close` called with `undefined`.

### T4 — `EditionFormComponent` → dialog content (create + edit)

Current file reads `ActivatedRoute.snapshot.paramMap.get('id')` to distinguish create/edit and injects `Router`/`RouterLink` to navigate to `/admin/editions` on cancel and after submit. Replace both with `DIALOG_DATA`/`DialogRef`.

```typescript
// edition-form.component.ts
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { firstValueFrom } from 'rxjs';
import { EditionService } from '../../../services/edition.service';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { DialogShellComponent } from '../../../shared/components/dialog-shell/dialog-shell.component';
import { maxDecimalsValidator } from '../../../shared/validators/financial.validators';

export interface EditionFormDialogData {
  editionId: number | null; // null = create mode
}

@Component({
  selector: 'app-edition-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslatePipe, NotificationInlineComponent, DialogShellComponent
  ],
  templateUrl: './edition-form.component.html',
})
export class EditionFormComponent implements OnInit {
  private readonly editionService = inject(EditionService);
  private readonly instanceConfigService = inject(GlobalInstanceConfigService);
  private readonly toast = inject(ToastService);
  private readonly translate = inject(TranslateService);
  private readonly fb = inject(FormBuilder);

  readonly dialogRef = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<EditionFormDialogData>(DIALOG_DATA);

  readonly isEditMode = computed(() => this.data.editionId !== null);
  readonly isLoading = signal(false);
  readonly isSaving = signal(false);
  readonly formError = signal<string | null>(null);
  readonly loadedEditionName = signal<string | null>(null);

  readonly dialogTitle = computed(() =>
    this.isEditMode() ? (this.loadedEditionName() ?? '') : this.translate.instant('edition.create.title')
  );
  readonly submitKey = computed(() =>
    this.isEditMode() ? 'edition.edit.submit' : 'edition.create.submit'
  );
  readonly cancelKey = computed(() =>
    this.isEditMode() ? 'edition.edit.cancel' : 'edition.create.cancel'
  );

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    commissionRate: [0, [Validators.required, Validators.min(0), Validators.max(100), maxDecimalsValidator(2)]],
    documentLanguage: ['EN' as 'EN' | 'FR', [Validators.required]],
    startDate: [null as string | null],
    endDate: [null as string | null]
  });

  async ngOnInit(): Promise<void> {
    if (this.data.editionId !== null) {
      await this.loadEdition(this.data.editionId);
    } else {
      await this.loadDefaults();
    }
  }

  private async loadEdition(id: number): Promise<void> {
    this.isLoading.set(true);
    try {
      const edition = await firstValueFrom(this.editionService.getById(id));
      this.loadedEditionName.set(edition.name);
      this.form.patchValue({
        name: edition.name,
        commissionRate: edition.commissionRate,
        documentLanguage: edition.documentLanguage,
        startDate: edition.startDate,
        endDate: edition.endDate
      });
      if (edition.phase !== 'PREPARATION') {
        this.form.controls.commissionRate.disable();
      }
    } catch {
      this.formError.set('edition.edit.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  private async loadDefaults(): Promise<void> {
    this.isLoading.set(true);
    try {
      const config = await firstValueFrom(this.instanceConfigService.getConfig());
      this.form.patchValue({
        commissionRate: config.defaultCommissionRate,
        documentLanguage: config.defaultDocumentLanguage as 'EN' | 'FR'
      });
    } catch {
      // Non-critical: form defaults remain (0 / EN)
    } finally {
      this.isLoading.set(false);
    }
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid || this.isSaving()) {
      return;
    }
    this.isSaving.set(true);
    this.formError.set(null);
    try {
      const { name, commissionRate, documentLanguage, startDate, endDate } = this.form.getRawValue();
      const payload = {
        name,
        commissionRate: this.form.controls.commissionRate.disabled ? null : commissionRate,
        documentLanguage,
        startDate: startDate || null,
        endDate: endDate || null
      };
      if (this.isEditMode() && this.data.editionId !== null) {
        await firstValueFrom(this.editionService.update(this.data.editionId, payload));
        this.toast.showSuccess(this.translate.instant('edition.edit.success'));
      } else {
        await firstValueFrom(this.editionService.create(payload));
        this.toast.showSuccess(this.translate.instant('edition.create.success'));
      }
      this.dialogRef.close();
    } catch (err: unknown) {
      if (err instanceof HttpErrorResponse && err.status === 422) {
        const errorType: string = (err.error as { type?: string })?.type ?? '';
        if (errorType.endsWith('/commission-rate-frozen')) {
          this.formError.set('edition.edit.error.commissionRateFrozen');
        } else if (errorType.endsWith('/edition-already-active')) {
          this.formError.set('edition.create.error.alreadyActive');
        } else {
          const key422 = this.isEditMode() ? 'edition.edit.error.save' : 'edition.create.error.save';
          this.toast.showError(this.translate.instant(key422));
        }
      } else {
        const key = this.isEditMode() ? 'edition.edit.error.save' : 'edition.create.error.save';
        this.toast.showError(this.translate.instant(key));
      }
    } finally {
      this.isSaving.set(false);
    }
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
```

`MatIconModule`/`RouterLink` imports are dropped (no icon-only back button anymore — the shell owns the close icon).

```html
<!-- edition-form.component.html -->
<app-dialog-shell [title]="dialogTitle()" (close)="cancel()">
  @if (formError()) {
    <app-notification-inline [message]="formError()! | translate" variant="error" />
  }

  <form [formGroup]="form" (ngSubmit)="onSubmit()" class="dialog-form">
    <mat-form-field appearance="outline">
      <mat-label>{{ 'edition.create.name.label' | translate }}</mat-label>
      <input matInput formControlName="name" type="text" />
      @if (form.controls.name.errors?.['required'] && form.controls.name.touched) {
        <mat-error>{{ 'edition.create.name.required' | translate }}</mat-error>
      }
      @if (form.controls.name.errors?.['maxlength'] && form.controls.name.touched) {
        <mat-error>{{ 'edition.create.name.maxLength' | translate }}</mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'edition.create.commissionRate.label' | translate }}</mat-label>
      <input matInput formControlName="commissionRate" type="number" step="0.01" [min]="0" [max]="100" />
      @if (form.controls.commissionRate.errors?.['required'] && form.controls.commissionRate.touched) {
        <mat-error>{{ 'edition.create.commissionRate.required' | translate }}</mat-error>
      }
      @if (form.controls.commissionRate.errors?.['min'] && form.controls.commissionRate.touched) {
        <mat-error>{{ 'edition.create.commissionRate.min' | translate }}</mat-error>
      }
      @if (form.controls.commissionRate.errors?.['max'] && form.controls.commissionRate.touched) {
        <mat-error>{{ 'edition.create.commissionRate.max' | translate }}</mat-error>
      }
      @if (form.controls.commissionRate.errors?.['maxDecimals'] && form.controls.commissionRate.touched) {
        <mat-error>{{ 'edition.create.commissionRate.maxDecimals' | translate }}</mat-error>
      }
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'edition.create.documentLanguage.label' | translate }}</mat-label>
      <mat-select formControlName="documentLanguage">
        <mat-option value="EN">{{ 'admin.settings.language.EN' | translate }}</mat-option>
        <mat-option value="FR">{{ 'admin.settings.language.FR' | translate }}</mat-option>
      </mat-select>
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'edition.form.startDate' | translate }}</mat-label>
      <input matInput formControlName="startDate" type="date" />
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'edition.form.endDate' | translate }}</mat-label>
      <input matInput formControlName="endDate" type="date" />
    </mat-form-field>

    <div class="dialog__actions">
      <button type="button" mat-button (click)="cancel()">
        {{ cancelKey() | translate }}
      </button>
      <button type="submit" mat-flat-button color="primary" [disabled]="form.invalid || isSaving()">
        {{ submitKey() | translate }}
      </button>
    </div>
  </form>
</app-dialog-shell>
```

All field markup is unchanged from the current file — only the outer `<div class="card form-card"><div class="card__header">...</div><div class="card__body">` wrapper is replaced by `<app-dialog-shell>`, and `class="card__actions"` becomes `class="dialog__actions"` (see T12.3).

**Spec rewrite (T4.3)** — remove `provideRouter(...)`, add `DIALOG_DATA`/`DialogRef` providers:

```typescript
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
// ...
const dialogRefMock = { close: vi.fn() };

// create mode:
providers: [
  provideTranslateService({ lang: 'en' }),
  { provide: EditionService, useValue: editionServiceMock },
  { provide: GlobalInstanceConfigService, useValue: instanceConfigMock },
  { provide: ToastService, useValue: toastMock },
  { provide: DIALOG_DATA, useValue: { editionId: null } },
  { provide: DialogRef, useValue: dialogRefMock },
],
```

Existing tests stay valid (they exercise create mode already). Add:
- `it('calls editionService.update and closes the dialog in edit mode', ...)` — rebuild `TestBed` with `{ editionId: 1 }` and `editionServiceMock.getById` returning a mock edition; assert `update` called and `dialogRefMock.close` called.
- `it('cancel() closes the dialog', () => { component.cancel(); expect(dialogRefMock.close).toHaveBeenCalledOnce(); })`
- `it('closes the dialog after a successful create', async () => { ...; await component.onSubmit(); expect(dialogRefMock.close).toHaveBeenCalledOnce(); })`

### T5 — `PhaseControlComponent` → dialog content

```typescript
// phase-control.component.ts — relevant diff
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DialogShellComponent } from '../../../../shared/components/dialog-shell/dialog-shell.component';
// remove: ActivatedRoute, RouterLink

export interface PhaseControlDialogData {
  editionId: number;
}

@Component({
  selector: 'app-phase-control',
  standalone: true,
  imports: [TranslatePipe, MatButtonModule, MatIconModule, SkeletonRowComponent, NotificationInlineComponent, DialogShellComponent],
  templateUrl: './phase-control.component.html',
  styleUrl: './phase-control.component.scss',
})
export class PhaseControlComponent implements OnInit {
  // ... unchanged injections (editionService, confirmDialog, toast, translate, destroyRef) ...
  readonly dialogRef = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<PhaseControlDialogData>(DIALOG_DATA);

  // ... unchanged signals: edition, isLoading, isSubmitting, error ...

  async ngOnInit(): Promise<void> {
    this.isLoading.set(true);
    this.error.set(null);
    try {
      this.edition.set(await firstValueFrom(this.editionService.getById(this.data.editionId)));
    } catch {
      this.error.set('phase.control.error.load');
    } finally {
      this.isLoading.set(false);
    }
  }

  // canAdvance/canRollback/nextPhase/prevPhase/confirmAdvance/confirmRollback: UNCHANGED
}
```

```html
<!-- phase-control.component.html -->
<app-dialog-shell [title]="'phase.control.title' | translate: { editionName: edition()?.name ?? '' }" (close)="dialogRef.close()">
  @if (isLoading()) {
    <app-skeleton-row [rows]="3" />
  }

  @if (error()) {
    <app-notification-inline [message]="error()! | translate" variant="error" />
  }

  @if (!isLoading() && !error() && edition()) {
    <div class="phase-control">
      <div class="phase-control__current">
        <span class="phase-chip">
          <span class="badge__dot" aria-hidden="true"></span>
          {{ ('edition.phase.' + edition()!.phase) | translate }}
        </span>
      </div>

      <div class="phase-control__actions">
        @if (canRollback()) {
          <button type="button" mat-button [disabled]="isSubmitting()" (click)="confirmRollback()">
            <mat-icon>arrow_back</mat-icon>
            {{ 'phase.rollback.button' | translate }} {{ ('edition.phase.' + prevPhase()) | translate }}
          </button>
        }

        @if (canAdvance()) {
          <button type="button" mat-flat-button color="primary" [disabled]="isSubmitting()" (click)="confirmAdvance()">
            {{ 'phase.advance.button' | translate }} {{ ('edition.phase.' + nextPhase()) | translate }}
            <mat-icon>arrow_forward</mat-icon>
          </button>
        }
      </div>
    </div>
  }
</app-dialog-shell>
```

`phase-control.component.scss` needs no changes — `.phase-control`, `.phase-control__current`, `.phase-control__actions`, `.phase-chip` are body-content rules, unaffected by the container swap. Only the removed `<div class="card">`/`card--narrow` usage (page-level, defined in global `styles.scss`) goes away — see T12.1.

**Nested dialog stacking (AC 3):** `confirmAdvance()`/`confirmRollback()` call `ConfirmDialogService.open(...)`, which opens a second, independent `Dialog` on top of the phase-control dialog. Angular CDK's `Dialog` service supports multiple simultaneously-open dialogs natively (each gets its own overlay pane; CDK Overlay stacks z-index automatically) — this already works today when `ConfirmDialogService` is opened from a normal (non-dialog) page, and nothing about `PhaseControlComponent` itself becoming a dialog changes that mechanism. **Manually verify in the browser** after implementing (open "Gérer les phases", click "Passer en phase Vente", confirm the nested confirm-dialog appears above it, its own focus trap works, and closing it returns focus correctly to the phase-control dialog) — this exact stacking scenario is new territory for this app and is not something the automated spec (which mocks `ConfirmDialogService`) can catch.

**Spec rewrite (T5.3):** replace
```typescript
{ provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
```
with
```typescript
{ provide: DIALOG_DATA, useValue: { editionId: 1 } },
{ provide: DialogRef, useValue: { close: vi.fn() } },
```
and drop `provideRouter([])` (no longer needed — `RouterLink` is gone). All existing test bodies (`loads edition on init`, `canAdvance`, `confirmAdvance`, etc.) are unchanged since they only touch `editionServiceMock`/`confirmMock`/`toastMock`, not routing.

### T6 — `EditionCategoriesComponent` → dialog content

```typescript
// edition-categories.component.ts — relevant diff
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { DialogShellComponent } from '../../../../shared/components/dialog-shell/dialog-shell.component';
// remove: ActivatedRoute, RouterLink

export interface EditionCategoriesDialogData {
  editionId: number;
}

@Component({
  selector: 'app-edition-categories',
  standalone: true,
  imports: [
    FormsModule,
    MatButtonModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    TranslatePipe, NotificationInlineComponent, SkeletonRowComponent, DialogShellComponent
  ],
  templateUrl: './edition-categories.component.html',
})
export class EditionCategoriesComponent implements OnInit {
  // ... unchanged injections ...
  readonly dialogRef = inject<DialogRef<void>>(DialogRef);
  readonly data = inject<EditionCategoriesDialogData>(DIALOG_DATA);

  // ... unchanged signals ...
  private editionId = 0;

  async ngOnInit(): Promise<void> {
    this.editionId = this.data.editionId;
    this.isLoading.set(true);
    this.error.set(null);
    try {
      const [ed, cats, allEditions] = await Promise.all([
        firstValueFrom(this.editionService.getById(this.editionId)),
        firstValueFrom(this.categoryService.getCategories(this.editionId)),
        firstValueFrom(this.editionService.getAll()),
      ]);
      this.edition.set(ed);
      this.categories = cats.map(c => this.toRow(c));
      this.closedEditions.set(allEditions.filter(e => e.phase === 'CLOSED' && e.id !== this.editionId));
    } catch {
      this.error.set('category.load.error');
    } finally {
      this.isLoading.set(false);
    }
  }

  // addCategory/removeCategory/onSave/onCopy/onSelectSource/nameErrorMatcher/tableErrorMatcher/
  // validateRows/parseTableInput/toRow: ALL UNCHANGED
}
```

The `rawId`/`isNaN`/`<= 0` guard block from the old `ngOnInit` is **removed** — the caller (`EditionListComponent`, T8) always passes a valid numeric `editionId` from an already-loaded `EditionDto`, so the defensive parsing that existed only because `ActivatedRoute` params are untyped strings is no longer needed (per project guidance: don't validate for scenarios that can't happen).

```html
<!-- edition-categories.component.html — top of file -->
<app-dialog-shell [title]="'category.title' | translate: { editionName: edition()?.name ?? '' }" (close)="dialogRef.close()">
  @if (isLoading()) {
    <app-skeleton-row [rows]="3" />
  }

  @if (error()) {
    <app-notification-inline [message]="error()! | translate" variant="error" />
  }

  @if (!isLoading() && !error()) {
    <!-- unchanged: isReadOnly banner, category-copy-section, data-table, category-actions -->
  }
</app-dialog-shell>
```

Remove the old `<div class="card card--list card--narrow"><div class="card__header"><div class="card__header-title"><a routerLink="/admin/editions" mat-icon-button ...><mat-icon>arrow_back</mat-icon></a><h1 class="card__title">...</h1></div></div>` block entirely — `DialogShellComponent` now owns title + close. Everything from `@if (isLoading())` onward is unchanged and simply becomes a direct child of `<app-dialog-shell>` instead of a sibling of `.card__header` inside `.card`.

**Spec rewrite (T6.3):** same substitution as T5.3 — replace the `ActivatedRoute` provider with `{ provide: DIALOG_DATA, useValue: { editionId: 1 } }` and `{ provide: DialogRef, useValue: { close: vi.fn() } }`; drop `provideRouter([])`. All existing test bodies are unchanged (they exercise `categoryServiceMock`/`editionServiceMock`, not routing).

### T7 — `UserFormComponent` → dialog content (create only)

No `DIALOG_DATA` needed — this form has no pre-fill mode.

```typescript
// user-form.component.ts
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DialogRef } from '@angular/cdk/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { firstValueFrom } from 'rxjs';
import { UserService } from '../../../services/user.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { DialogShellComponent } from '../../../shared/components/dialog-shell/dialog-shell.component';
import { passwordStrengthValidators } from '../../../shared/validators/password-strength.validator';

@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslatePipe, NotificationInlineComponent, DialogShellComponent],
  templateUrl: './user-form.component.html'
})
export class UserFormComponent {
  private readonly userService = inject(UserService);
  private readonly fb = inject(FormBuilder);
  readonly dialogRef = inject<DialogRef<void>>(DialogRef);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(50)]],
    lastName: ['', [Validators.required, Validators.maxLength(50)]],
    username: ['', [Validators.required, Validators.maxLength(50)]],
    password: ['', [Validators.required, Validators.maxLength(128), ...passwordStrengthValidators]],
    role: ['VOLUNTEER' as const],
  });

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    try {
      await firstValueFrom(this.userService.createVolunteer(this.form.getRawValue()));
      this.dialogRef.close();
    } catch {
      this.error.set('admin.users.error.create');
    } finally {
      this.loading.set(false);
    }
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
```

```html
<!-- user-form.component.html -->
<app-dialog-shell [title]="'admin.users.create.title' | translate" (close)="cancel()">
  @if (error()) {
    <app-notification-inline [message]="error()! | translate" variant="error" />
  }

  <form [formGroup]="form" (ngSubmit)="onSubmit()" class="dialog-form">
    <div class="form-row">
      <mat-form-field appearance="outline">
        <mat-label>{{ 'admin.users.create.firstName' | translate }}</mat-label>
        <input matInput formControlName="firstName" type="text" autocomplete="given-name" />
      </mat-form-field>
      <mat-form-field appearance="outline">
        <mat-label>{{ 'admin.users.create.lastName' | translate }}</mat-label>
        <input matInput formControlName="lastName" type="text" autocomplete="family-name" />
      </mat-form-field>
    </div>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'admin.users.create.username' | translate }}</mat-label>
      <input matInput formControlName="username" type="text" autocomplete="username" />
    </mat-form-field>

    <mat-form-field appearance="outline">
      <mat-label>{{ 'admin.users.create.password' | translate }}</mat-label>
      <input matInput formControlName="password" type="password" autocomplete="new-password" />
      @if (form.controls.password.hasError('required')) {
        <mat-error>{{ 'admin.users.create.passwordRequired' | translate }}</mat-error>
      }
      @if (form.controls.password.hasError('minlength')) {
        <mat-error>{{ 'admin.users.create.passwordMinLength' | translate }}</mat-error>
      }
      @if (form.controls.password.hasError('needsUppercase')) {
        <mat-error>{{ 'common.password.needsUppercase' | translate }}</mat-error>
      }
      @if (form.controls.password.hasError('needsDigit')) {
        <mat-error>{{ 'common.password.needsDigit' | translate }}</mat-error>
      }
    </mat-form-field>

    <div class="dialog__actions">
      <button type="button" mat-button (click)="cancel()">
        {{ 'admin.users.create.cancel' | translate }}
      </button>
      <button type="submit" mat-flat-button color="primary" [disabled]="form.invalid || loading()">
        {{ 'admin.users.create.submit' | translate }}
      </button>
    </div>
  </form>
</app-dialog-shell>
```

`.form-row` is an existing global `styles.scss` class (`display: grid; grid-template-columns: 1fr 1fr;`) — unchanged, still applies.

**Spec rewrite (T7.3):** remove `provideRouter(...)` and the `Router`/`navigateSpy` pattern; add `{ provide: DialogRef, useValue: { close: vi.fn() } }`. Replace:
- `'calls createVolunteer with form values on valid submit and navigates to the list'` → assert `dialogRefMock.close` called instead of `navigateSpy`
- `'cancel() navigates back to the user list'` → `it('cancel() closes the dialog', () => { component.cancel(); expect(dialogRefMock.close).toHaveBeenCalledOnce(); })` (no longer `async` — `cancel()` is now synchronous)

### T8 — `EditionListComponent` wiring

```typescript
// edition-list.component.ts — relevant diff
import { Dialog } from '@angular/cdk/dialog';
import { EditionFormComponent, EditionFormDialogData } from './edition-form.component';
import { PhaseControlComponent, PhaseControlDialogData } from './phase-control/phase-control.component';
import { EditionCategoriesComponent, EditionCategoriesDialogData } from './edition-categories/edition-categories.component';
// remove: Router

private readonly dialog = inject(Dialog);
// remove: private readonly router = inject(Router);

async ngOnInit(): Promise<void> {
  await this.loadEditions();
}

private async loadEditions(): Promise<void> {
  this.isLoading.set(true);
  this.error.set(null);
  try {
    this.editions.set(await firstValueFrom(this.editionService.getAll()));
  } catch {
    this.error.set('edition.actions.error.load');
  } finally {
    this.isLoading.set(false);
  }
}

private openEditionDialog(editionId: number | null): void {
  const ref = this.dialog.open<void, EditionFormDialogData, EditionFormComponent>(
    EditionFormComponent,
    {
      data: { editionId },
      hasBackdrop: true,
      backdropClass: 'dialog-backdrop',
      panelClass: 'dialog-panel',
      disableClose: false,
      ariaLabel: this.translate.instant(editionId === null ? 'edition.create.title' : 'edition.edit.title'),
    }
  );
  ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadEditions());
}

openCreateDialog(): void {
  this.openEditionDialog(null);
}

openEditDialog(edition: EditionDto): void {
  this.openEditionDialog(edition.id);
}

isEditable(edition: EditionDto): boolean {
  return edition.phase === 'PREPARATION'; // unchanged
}

openPhaseDialog(edition: EditionDto): void {
  const ref = this.dialog.open<void, PhaseControlDialogData, PhaseControlComponent>(
    PhaseControlComponent,
    {
      data: { editionId: edition.id },
      hasBackdrop: true,
      backdropClass: 'dialog-backdrop',
      panelClass: 'dialog-panel',
      disableClose: false,
      ariaLabel: edition.name,
    }
  );
  ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadEditions());
}

openCategoriesDialog(edition: EditionDto): void {
  this.dialog.open<void, EditionCategoriesDialogData, EditionCategoriesComponent>(
    EditionCategoriesComponent,
    {
      data: { editionId: edition.id },
      hasBackdrop: true,
      backdropClass: 'dialog-backdrop',
      panelClass: 'dialog-panel',
      disableClose: false,
      ariaLabel: edition.name,
    }
  );
}

// confirmDelete(): UNCHANGED
```

`openPhaseDialog` refetches on close (the row's phase chip must reflect a possible phase change); `openCategoriesDialog` does not (categories are not shown in the list table, no refetch needed).

```html
<!-- edition-list.component.html — relevant diff -->
<div class="card__header">
  <h1 class="card__title">{{ 'edition.list.title' | translate }}</h1>
  <button type="button" mat-flat-button color="primary" (click)="openCreateDialog()">
    {{ 'edition.list.createButton' | translate }}
  </button>
</div>

<!-- empty state -->
<app-empty-state
  icon="event"
  [message]="'edition.list.empty' | translate"
  [actionLabel]="'edition.list.emptyAction' | translate"
  (action)="openCreateDialog()"
/>

<!-- row actions -->
<div class="actions-cell">
  <button type="button" mat-button (click)="openPhaseDialog(edition)">
    {{ 'edition.actions.managePhase' | translate }}
  </button>
  <button type="button" mat-button (click)="openCategoriesDialog(edition)">
    {{ 'edition.actions.manageCategories' | translate }}
  </button>
  <button type="button" mat-button (click)="openEditDialog(edition)">
    <mat-icon>edit</mat-icon>
    {{ 'edition.actions.edit' | translate }}
  </button>
  @if (isEditable(edition)) {
    <button type="button" mat-button color="warn" (click)="confirmDelete(edition)">
      <mat-icon>delete</mat-icon>
      {{ 'edition.actions.delete' | translate }}
    </button>
  }
</div>
```

Remove `RouterLink` from the `@Component` `imports` array (no `routerLink`/`[routerLink]` remains anywhere in this template after this change).

**Spec rewrite (T8.3):** add `{ provide: Dialog, useValue: dialogMock }` with `const dialogMock = { open: vi.fn().mockReturnValue({ closed: of(undefined) }) };`. Remove `provideRouter([])` and the `router = TestBed.inject(Router)` line/import entirely — after this task, nothing in `EditionListComponent` or its template touches `Router` anymore (the only prior usage, `navigateToEdit`'s test, is deleted below). Replace:
```typescript
it('navigateToEdit navigates to the edition edit route', () => {
  const spy = vi.spyOn(router, 'navigateByUrl');
  component.navigateToEdit(MOCK_EDITIONS[0]);
  expect(spy).toHaveBeenCalledWith('/admin/editions/1/edit');
});
```
with dialog-open tests, e.g.:
```typescript
it('openEditDialog opens EditionFormComponent with the edition id', () => {
  component.openEditDialog(MOCK_EDITIONS[0]);
  expect(dialogMock.open).toHaveBeenCalledWith(
    EditionFormComponent,
    expect.objectContaining({ data: { editionId: 1 } })
  );
});

it('openCreateDialog opens EditionFormComponent with editionId null', () => {
  component.openCreateDialog();
  expect(dialogMock.open).toHaveBeenCalledWith(
    EditionFormComponent,
    expect.objectContaining({ data: { editionId: null } })
  );
});

it('reloads the edition list after the edition dialog closes', async () => {
  editionServiceMock.getAll.mockClear();
  component.openCreateDialog();
  await fixture.whenStable();
  expect(editionServiceMock.getAll).toHaveBeenCalledTimes(1);
});

it('openPhaseDialog opens PhaseControlComponent with the edition id', () => {
  component.openPhaseDialog(MOCK_EDITIONS[0]);
  expect(dialogMock.open).toHaveBeenCalledWith(
    PhaseControlComponent,
    expect.objectContaining({ data: { editionId: 1 } })
  );
});

it('openCategoriesDialog opens EditionCategoriesComponent with the edition id', () => {
  component.openCategoriesDialog(MOCK_EDITIONS[0]);
  expect(dialogMock.open).toHaveBeenCalledWith(
    EditionCategoriesComponent,
    expect.objectContaining({ data: { editionId: 1 } })
  );
});
```
Remove the `router`/`Router` import and injection from the spec if no longer used (check — `confirmDelete` tests don't use `router`, so it can be fully removed once `navigateToEdit`'s test is gone). Keep `provideTranslateService` — `ariaLabel` calls now use `this.translate.instant(...)`, but that's already available.

### T9 — `UserListComponent` wiring

```typescript
// user-list.component.ts — relevant diff
import { UserFormComponent } from './user-form.component';
// remove: Router, RouterLink

async ngOnInit(): Promise<void> {
  await this.loadUsers();
}

private async loadUsers(): Promise<void> {
  this.isLoading.set(true);
  this.error.set(null);
  try {
    this.users.set(await firstValueFrom(this.userService.getVolunteers()));
  } catch {
    this.error.set('admin.users.error.load');
  } finally {
    this.isLoading.set(false);
  }
}

openCreateDialog(): void {
  const ref = this.dialog.open<void, void, UserFormComponent>(
    UserFormComponent,
    {
      hasBackdrop: true,
      backdropClass: 'dialog-backdrop',
      panelClass: 'dialog-panel',
      disableClose: false,
      ariaLabel: this.translate.instant('admin.users.create.title'),
    }
  );
  ref.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.loadUsers());
}

// toggleEnabled/confirmDelete/openResetPasswordDialog: UNCHANGED (openResetPasswordDialog gets the T3.4 ariaLabel tweak only)
// remove: navigateToCreate()
```

`this.dialog` (the `Dialog` service) is already injected in this component for `openResetPasswordDialog` — reuse it, no new injection needed.

```html
<!-- user-list.component.html -->
<div class="card__header">
  <h1 class="card__title">{{ 'admin.users.title' | translate }}</h1>
  <button type="button" mat-flat-button color="primary" (click)="openCreateDialog()">
    {{ 'admin.users.actions.create' | translate }}
  </button>
</div>
```

Remove `RouterLink` from the `@Component` `imports` array once no `routerLink` remains in this template (double-check — `user-list.component.html` should have no other router usage after this change).

**Spec rewrite (T9.3):** the `dialogMock` and `Dialog` provider already exist in this spec (reused from `openResetPasswordDialog` tests). Add:
```typescript
it('openCreateDialog opens UserFormComponent', () => {
  component.openCreateDialog();
  expect(dialogMock.open).toHaveBeenCalledWith(UserFormComponent, expect.anything());
});

it('reloads the user list after the create dialog closes', async () => {
  dialogMock.open.mockReturnValueOnce({ closed: of(undefined) });
  userServiceMock.getVolunteers.mockClear();
  component.openCreateDialog();
  await fixture.whenStable();
  expect(userServiceMock.getVolunteers).toHaveBeenCalledTimes(1);
});
```
No `navigateToCreate` test currently exists in this spec file to remove (confirmed by re-reading the file — it wasn't covered).

### T10 — `admin.routes.ts`

```typescript
import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  {
    path: '',
    redirectTo: 'users',
    pathMatch: 'full'
  },
  {
    path: 'users',
    loadComponent: () =>
      import('./users/user-list.component').then((m) => m.UserListComponent),
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./settings/admin-settings.component').then((m) => m.AdminSettingsComponent),
  },
  {
    path: 'editions',
    loadComponent: () =>
      import('./editions/edition-list.component').then((m) => m.EditionListComponent),
  },
  {
    path: 'sellers',
    loadComponent: () =>
      import('./sellers/seller-list.component').then((m) => m.SellerListComponent),
  },
];
```

`EditionFormComponent`, `PhaseControlComponent`, `EditionCategoriesComponent`, `UserFormComponent` are no longer route-loaded — they're instantiated directly by `Dialog.open(...)` (T4/T5/T6/T7 already changed their `@Component` decorators to drop route-only concerns). No `app.routes.ts` change needed (only `admin.routes.ts` had these 5 entries).

### T11 — i18n cleanup

Before deleting, grep for each key to confirm no remaining reference (component templates, other i18n files, or `.ts` via `translate.instant(...)`):
```
grep -rn "category.back\|phase.control.back\|edition.create.backToList" pluribourse-frontend/src
```
After T5/T6 remove the only templates that referenced them (`phase-control.component.html`'s old back-arrow, `edition-categories.component.html`'s old back-arrow, `edition-form.component.html`'s old icon-button), these three keys should have zero remaining references. Remove them from both `en.json` and `fr.json`:
- `phase.control.back`
- `category.back`
- `edition.create.backToList`

Keep `edition.create.cancel` / `edition.edit.cancel` — still used by the in-form Cancel button (T4).

### T12 — `styles.scss` cleanup and consolidation

Remove (dead after T5/T6):
```scss
.card__header-title {
  display: flex;
  align-items: center;
  gap: var(--pb-space-sm);
}
```
```scss
.card--narrow {
  max-width: 640px;
  margin-inline: auto;
}
```
```scss
.card--narrow .data-table {
  th:first-child,
  td:first-child {
    padding-left: var(--pb-space-lg);
  }

  th:last-child,
  td:last-child {
    padding-right: var(--pb-space-lg);
  }
}
```
The last one is superseded, not just orphaned: `DialogShellComponent.__body` already applies `padding: var(--pb-space-lg)` on **all four sides**, so the categories table (now living inside `.dialog-shell__body`) automatically gets the same 24px left/right clearance this rule used to special-case — no replacement rule is needed.

Do **not** remove `.category-copy-section` or `.category-actions` — both style content that stays inside the dialog body unchanged (verify no other page still relies on `.card--narrow`/`.card__header-title` before deleting — a repo-wide grep confirms both were introduced this same session solely for `phase-control` and `edition-categories`, and are removed by T5/T6 in this same story, so this is safe).

Add (consolidated from the deleted `confirm-dialog.component.scss` / `reset-password-dialog.component.scss`, reused by every dialog-content component in this story):
```scss
// ── Dialog content utilities (paired with DialogShellComponent) ────────────
.dialog__description,
.dialog__desc {
  margin: 0 0 var(--pb-space-lg);
  font-size: 14px;
  color: var(--mat-sys-on-surface-variant);
  line-height: 1.5;
}

.dialog__field {
  width: 100%;
  margin-bottom: var(--pb-space-md);
}

.dialog__actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--pb-space-sm);
  margin-top: var(--pb-space-md);
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: var(--pb-space-md);

  mat-form-field {
    width: 100%;
  }
}
```
`ConfirmDialogComponent` used `.dialog__description` (long form); `ResetPasswordDialogComponent` used `.dialog__desc` (short form) — both selectors are kept (comma-grouped) rather than renaming either template, to minimize the diff on files not otherwise touched by this story beyond T2/T3.

### Existing Code NOT to Break

- `ConfirmDialogService.open()` return type (`Observable<boolean | undefined>`) and its consumers (`EditionListComponent.confirmDelete`, `UserListComponent.confirmDelete`, `PhaseControlComponent.confirmAdvance`/`confirmRollback`) — unchanged, only the internal `Dialog.open(...)` config's ARIA wiring changes (T2.4).
- `EditionService`, `CategoryService`, `UserService`, `GlobalInstanceConfigService` — no changes, all consumed identically.
- `edition-categories.component.ts` business logic (`addCategory`, `removeCategory`, `onSave`, `onCopy`, `validateRows`, `parseTableInput`, `toRow`, `nameErrorMatcher`, `tableErrorMatcher`) — untouched, only `ngOnInit`'s id-acquisition changes.
- `phase-control.component.ts` business logic (`canAdvance`, `canRollback`, `nextPhase`, `prevPhase`, `confirmAdvance`, `confirmRollback`) — untouched, only `ngOnInit`'s id-acquisition changes.
- `.dialog-backdrop` (styles.scss, Story 1.8) — unchanged, still referenced by every `Dialog.open(...)` call site.
- `.data-table`, `.category-copy-section`, `.category-actions`, `.phase-control*`, `.phase-chip` global/local rules — unchanged.
- `ChangePasswordComponent` — entirely unrelated to this story, do not touch.

### Test Patterns

Every converted dialog-content spec follows the same substitution:

| Before | After |
|---|---|
| `provideRouter([...])` + `{ provide: ActivatedRoute, useValue: {...} }` | `{ provide: DIALOG_DATA, useValue: {...} }` + `{ provide: DialogRef, useValue: { close: vi.fn() } }` |
| `vi.spyOn(router, 'navigate'/'navigateByUrl')` assertions | `expect(dialogRefMock.close).toHaveBeenCalled...` assertions |

Every list-component spec that gains a new dialog trigger follows:
```typescript
const dialogMock = { open: vi.fn().mockReturnValue({ closed: of(undefined) }) };
// provider: { provide: Dialog, useValue: dialogMock }

it('opens X with the expected data', () => {
  component.openXDialog(...);
  expect(dialogMock.open).toHaveBeenCalledWith(XComponent, expect.objectContaining({ data: {...} }));
});
```

Run `npm test` (in `pluribourse-frontend/`) after each task group, not just at the end — this story touches 13 existing spec files plus 1 new one; catching a broken import early is much cheaper than debugging a mass failure at T13.

### Project Structure Notes

**Files created:**
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.ts`
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.html`
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.scss`
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.spec.ts`

**Files deleted:**
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.scss`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.scss`

**Files modified:**
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.html`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.spec.ts`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.spec.ts`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.html`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.html`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.html`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/styles.scss`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`

**No backend changes** — every API call (`EditionService`, `CategoryService`, `UserService`, `GlobalInstanceConfigService`) is identical; only the frontend presentation container and navigation trigger change.

### References

- [Source: pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.ts] — established CDK Dialog pattern for a form-in-a-dialog (`DIALOG_DATA`, `DialogRef<string>`, `A11yModule`, `cdkFocusInitial`)
- [Source: pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts] — `Dialog.open(...)` config shape: `backdropClass: 'dialog-backdrop'`, `panelClass: 'dialog-panel'`, `disableClose: false`
- [Source: pluribourse-frontend/src/app/shared/components/empty-state/empty-state.component.ts] — signal-based `input()`/`output()` convention to follow for `DialogShellComponent`
- [Source: pluribourse-frontend/node_modules/@angular/cdk/types/dialog.d.ts:91] — `DialogConfig.ariaLabel?: string | null` — the literal-string alternative to `ariaLabelledBy` used throughout this story to avoid ID-collision on stacked/shared-shell dialogs
- [Source: pluribourse-frontend/src/app/features/admin/admin.routes.ts] — routes removed in T10
- [Source: pluribourse-frontend/src/styles.scss] — `.card`, `.card__header`, `.card--list`, `.form-card`, `.card--narrow`, `.card__header-title`, `.data-table` (Stories 1.7/2.5 + this session's page-layout fixes, superseded/cleaned up in T12) ; `.dialog-backdrop` (Story 1.8, unchanged)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/EXPERIENCE.md] — `DialogShellComponent` behavioral spec (updated this session, `status: final`, `updated: 2026-07-02`): focus trap, focus initial, focus restoration, `ariaLabel`/close semantics, 640px max-width, scrollable body
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-PluriBourse-2026-06-09/DESIGN.md] — `components.dialog` tokens: `max-width: '640px'`, `close-button: 'icon-button, top-right, 44x44px target, icône close'`
- [Source: _bmad-output/implementation-artifacts/1-10-ameliorations-ux-mots-de-passe.md] — prior story establishing the CDK Dialog + spec-mocking conventions this story extends

## Dev Agent Record

### Agent Model Used

Claude Sonnet 5 (claude-sonnet-5)

### Debug Log References

None — implementation followed the Dev Notes' prescribed code closely; no unexpected failures required debugging. `npm test` was run after each task group (T1, T3, T7, T13) per the story's recommended cadence, staying green throughout.

### Completion Notes List

- Created `DialogShellComponent` as the single shared dialog container (title, close button, scrollable body), following the existing signal-based `input()`/`output()` convention.
- Migrated `ConfirmDialogComponent` and `ResetPasswordDialogComponent` onto `DialogShellComponent`; both now use `ariaLabel` (literal string) instead of `ariaLabelledBy` to avoid id-collision risk with stacked dialogs.
- Converted `EditionFormComponent` (create + edit), `PhaseControlComponent`, `EditionCategoriesComponent`, and `UserFormComponent` from routed pages to dialog content, replacing `ActivatedRoute`/`Router`/`RouterLink` with `DIALOG_DATA`/`DialogRef`. Added edit-mode test coverage for `EditionFormComponent` (previously untested).
- Wired `EditionListComponent` (create/edit/phase/categories dialogs) and `UserListComponent` (create dialog) to open these components via `Dialog.open(...)`, replacing page navigation; list reloads on dialog close where the underlying data may have changed.
- Removed the 5 now-obsolete routes from `admin.routes.ts` (`users/create`, `editions/create`, `editions/:id/edit`, `editions/:id/phase`, `editions/:id/categories`).
- Removed 3 dead i18n keys (`category.back`, `phase.control.back`, `edition.create.backToList`) after confirming zero remaining references via grep.
- Cleaned up `styles.scss`: removed `.card__header-title`, `.card--narrow`, and the now-superseded `.card--narrow .data-table` edge-padding rule; added consolidated `.dialog__description`/`.dialog__desc`/`.dialog__field`/`.dialog__actions`/`.dialog-form` utility classes reused across all dialog-content components.
- Full suite: 39 test files, 274 tests, all passing, zero regressions.
- **Manual verification still recommended** (per Dev Notes, T5): the nested confirm-dialog stacking scenario (phase-control dialog → advance/rollback confirm dialog on top) works through CDK's native multi-dialog support, but wasn't exercised by an automated spec (which mocks `ConfirmDialogService`). Worth a quick manual check in the browser.

### File List

**Created:**
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.ts`
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.html`
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.scss`
- `pluribourse-frontend/src/app/shared/components/dialog-shell/dialog-shell.component.spec.ts`

**Deleted:**
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.scss`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.scss`

**Modified:**
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.ts`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.html`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.component.spec.ts`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.ts`
- `pluribourse-frontend/src/app/shared/components/confirm-dialog/confirm-dialog.service.spec.ts`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.html`
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/phase-control/phase-control.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-categories/edition-categories.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.html`
- `pluribourse-frontend/src/app/features/admin/users/user-form.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.html`
- `pluribourse-frontend/src/app/features/admin/editions/edition-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.ts`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.html`
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.spec.ts`
- `pluribourse-frontend/src/app/features/admin/admin.routes.ts`
- `pluribourse-frontend/src/styles.scss`
- `pluribourse-frontend/public/i18n/en.json`
- `pluribourse-frontend/public/i18n/fr.json`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.ts`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.html`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.scss`
- `pluribourse-frontend/src/app/layout/app-layout/app-layout.component.spec.ts`
- `pluribourse-frontend/src/app/features/account/account.component.ts`
- `pluribourse-frontend/src/app/features/account/account.component.html`

### Change Log

| Date | Change |
|---|---|
| 2026-07-02 | Story implemented: shared `DialogShellComponent` created; `ConfirmDialogComponent`, `ResetPasswordDialogComponent`, `EditionFormComponent`, `PhaseControlComponent`, `EditionCategoriesComponent`, `UserFormComponent` converted to dialog-based UI; obsolete routes and dead CSS/i18n removed. 274/274 tests passing. |
| 2026-07-02 | Bug fix (pre-existing, found during review): `category.locked.banner` always displayed "phase de dépôt" regardless of the edition's actual phase. The i18n key was a static string instead of being parameterized. Added `lockedPhaseLabel` computed on `EditionCategoriesComponent` and interpolated `{{phase}}` into the `en.json`/`fr.json` message. 276/276 tests passing. |
| 2026-07-02 | Bug fix (pre-existing, found during review): the topbar phase chip ("Aucune édition en cours") never refreshed after creating or deleting an edition — `CurrentEditionService.currentEdition` is only updated by SSE `phase-changed` events, which the backend only emits from `advancePhase`/`rollbackPhase`, not from `createEdition`/`deleteEdition`. `EditionListComponent` now calls `CurrentEditionService.loadEdition()` after the create/edit dialog closes and after a successful delete. 279/279 tests passing. |
| 2026-07-02 | UX change (user-requested): `EditionCategoriesComponent.onSave()` now closes the dialog after a successful save, matching the close-on-success pattern already used by `EditionFormComponent`/`UserFormComponent`. `PhaseControlComponent.confirmAdvance()`/`confirmRollback()` now also close the dialog after a successful phase transition — this **reverses the previously documented behavior** (EXPERIENCE.md Flow 4 described the dialog staying open so the admin could see the phase chip transition before manually closing); confirmed with the user before implementing, and Flow 4 has been updated accordingly. 286/286 tests passing. |
| 2026-07-03 | Multi-agent code review (Blind Hunter + Edge Case Hunter + Acceptance Auditor) — see Review Findings above. 9 patches applied: reactive i18n on `lockedPhaseLabel`/`dialogTitle` (`toSignal(translate.onLangChange)`); `openCategoriesDialog` now refreshes the list on close; `ariaLabel` synced to each dialog's actual visible title; `lockedPhaseLabel` tests now assert on a real translated string; restored `editionId` validation guards in `EditionCategoriesComponent`/`PhaseControlComponent`; added error handlers on `currentEditionService.loadEdition()` subscriptions; `dialogTitle` falls back to a translated label while loading; async-refresh tests now emit via a microtask-deferred observable instead of synchronously. Two flagged items resolved without code changes: theoretical double-dialog race on rapid double-click verified as already guarded by CDK's synchronous backdrop/focus-trap attachment (non-reproducible); route removal deep-linking loss confirmed as AC7-intended, not a defect. 287/287 tests passing. |
| 2026-07-03 | UX tweak (user-requested, post-review): "Gérer les phases"/"Gérer les catégories" row-action button labels shortened to "Phases"/"Catégories" (new `edition.actions.phases`/`edition.actions.categories` i18n keys) to reduce actions-cell width; added `mat-icon` (`timeline`/`category`) and `matTooltip` + `aria-label` using the original full-sentence text (`edition.actions.managePhase`/`edition.actions.manageCategories`) so the action remains unambiguous on hover and for screen readers. Considered and rejected: hiding the "Catégories" button outside `PREPARATION` phase — `EditionCategoriesComponent.isReadOnly()` intentionally keeps a read-only consultation mode across all phases (pre-existing, tested since Story 2.5); hiding the button would silently remove that capability, so the button stays visible in all phases. 287/287 tests passing. |
| 2026-07-03 | Regression fix (found via user question, out of diff scope for the original review — `app-layout.component.html` was never touched by this story so it never appeared in the reviewed diff): the admin topbar "phase chip" (`AppLayoutComponent`) still linked to `['/admin/editions', edition.id, 'phase']`, a route this story removed in T10 (AC7). Clicking it 404'd for every admin, on every page. Fixed with the minimal option (confirmed with user): the chip now links to `/admin/editions`, from which "Phases" reopens the dialog. Updated the corresponding spec (removed the dead route stub, updated the href assertion). 287/287 tests passing. |
| 2026-07-03 | Navigation gap fix (found via user question, pre-existing since Story 1.6/1.7, unrelated to this story's diff): `/account` (Story 1.6, language preference) had no entry point anywhere in the UI — only reachable by typing the URL. Added a "person" icon link in the topbar (`nav.account` tooltip/aria-label). 289/289 tests passing. |
| 2026-07-03 | Post-review UX changes (user-requested, out of this story's original scope — general navigation/account polish, not dialog work): (1) `AccountComponent` had never received a design pass (bare `<select>`, no `.card` wrapper) — restyled onto the same `.card.form-card` + `mat-form-field`/`mat-select` pattern as `admin-settings`. (2) The topbar's standalone "person" icon link (added above) and the separate "Déconnexion" button are replaced by a single `mat-icon-button` (`account_circle`, `matTooltip`/`aria-label` = `nav.userMenu`) opening a `mat-menu` with two `mat-menu-item`s: "Mon compte" (`routerLink="/account"`) and "Déconnexion" (`(click)="logout()"`). Updated the 3 topbar specs that queried the old flat DOM structure to open the menu first (CDK overlay renders into `document.body`, not the component's own `nativeElement`). 289/289 tests passing. |
