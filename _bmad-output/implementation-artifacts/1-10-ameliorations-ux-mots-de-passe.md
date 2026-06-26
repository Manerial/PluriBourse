---
baseline_commit: ddfc73ecb8d450759f6f04cfa2a89c75a1091eef
---

# Story 1.10: Password UX Improvements

Status: done

## Story

As a user managing passwords,
I want a confirmation field when changing my password and a dialog when an admin resets a volunteer's password,
so that I avoid typos and the UI is consistent with other destructive actions.

## Acceptance Criteria

1. **Given** a user is on `/change-password`, **When** they fill in the form, **Then** a second field « Confirmer le mot de passe » is present **And** the submit button remains disabled if the two fields do not match **And** an inline error message indicates the passwords do not match.

2. **Given** the admin clicks « Réinitialiser le mot de passe » in the volunteer list, **When** the click occurs, **Then** a CDK Dialog opens with the volunteer's display name, a password field, and Confirm / Cancel buttons **And** the password field applies the same validation rules as the change-password form (required, minLength 8, uppercase, digit).

3. **Given** the admin confirms the reset in the dialog, **When** the API call succeeds, **Then** the dialog closes and a success toast is shown.

4. **Given** the admin cancels or closes the dialog (Cancel button or Escape key), **When** the dismissal is triggered, **Then** no API call is made.

## Tasks / Subtasks

- [x] **T1 — `ChangePasswordComponent`: add confirm password field** (AC: 1)
  - [x] T1.1 — Update `change-password.component.ts`: add `confirmPassword` control to the form group + add cross-field validator `passwordsMatchValidator` as a form-level `ValidatorFn` (see Dev Notes)
  - [x] T1.2 — Update `change-password.component.html`: add a second `<mat-form-field>` for `confirmPassword` with `<mat-error>` for mismatch (see Dev Notes)
  - [x] T1.3 — Add i18n keys to `en.json` and `fr.json` (see Dev Notes)
  - [x] T1.4 — Update `change-password.component.spec.ts`: add tests for mismatch validation and confirm field

- [x] **T2 — `ResetPasswordDialogComponent`** (AC: 2, 3, 4)
  - [x] T2.1 — Create `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.ts` (see Dev Notes)
  - [x] T2.2 — Create `reset-password-dialog.component.html` (see Dev Notes)
  - [x] T2.3 — Create `reset-password-dialog.component.scss` (reuse confirm-dialog styles via `.dialog` class — see Dev Notes)
  - [x] T2.4 — Add i18n keys for the dialog to `en.json` and `fr.json` (see Dev Notes)

- [x] **T3 — `UserListComponent`: replace inline form with dialog** (AC: 2, 3, 4)
  - [x] T3.1 — Update `user-list.component.ts`: inject `Dialog` from `@angular/cdk/dialog`, add `ResetPasswordDialogComponent` to imports, remove `resetPasswordFor` signal, `resetPasswordForm`, `showResetPassword()`, `cancelResetPassword()`, rewrite `submitResetPassword()` into `openResetPasswordDialog(user: UserDto)` (see Dev Notes)
  - [x] T3.2 — Update `user-list.component.html`: replace the `@if (resetPasswordFor() !== user.id) { ... } @else { form... }` block (lines 60–79) with a single button that calls `openResetPasswordDialog(user)` (see Dev Notes)
  - [x] T3.3 — Update `user-list.component.scss`: remove dead rules `.reset-form` (lines 65–70) and `.reset-input` (lines 72–87) — these styled the now-removed inline form
  - [x] T3.4 — Update `user-list.component.spec.ts`: remove tests for inline form (`showResetPassword sets resetPasswordFor signal`, `shows success toast after resetting password`), add tests for dialog invocation (see Dev Notes)

- [x] **T4 — Run `npm test`** — all existing tests must pass, zero regressions

## Dev Notes

### T1 — Cross-field validator pattern (Angular reactive forms)

Cross-field validators are applied at the **form group level**, not the individual control level. The validator function receives the `AbstractControl` (the group) and returns a `ValidationErrors | null`.

```typescript
// change-password.component.ts

import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

function passwordsMatchValidator(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get('newPassword')?.value;
    const confirm = group.get('confirmPassword')?.value;
    if (confirm && password !== confirm) {
      return { passwordsMismatch: true };
    }
    return null;
  };
}

// Form group (second arg to .group() is the group-level options including validators):
readonly form = this.fb.nonNullable.group(
  {
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*[A-Z].*/), Validators.pattern(/.*[0-9].*/)]],
    confirmPassword: ['', Validators.required]
  },
  { validators: passwordsMatchValidator() }
);
```

The error `passwordsMismatch` is on `form` (the group), not on `confirmPassword` control. In the template, check it with `form.hasError('passwordsMismatch')`.

The submit button should be disabled when `form.invalid`. The form is invalid when:
- Either control is invalid (empty or fails newPassword rules)
- OR the group-level `passwordsMismatch` error is present

```typescript
// onSubmit stays the same — still uses:
this.form.getRawValue().newPassword
```

### T1 — `change-password.component.html` update

Add the second `mat-form-field` after the first one, and update the submit button's disabled condition. The group-level error is accessed via `form.hasError('passwordsMismatch')` (not via a control):

```html
<mat-form-field appearance="outline">
  <mat-label>{{ 'auth.changePassword.confirmPassword' | translate }}</mat-label>
  <input matInput formControlName="confirmPassword" type="password" autocomplete="new-password" />
  @if (form.hasError('passwordsMismatch') && form.get('confirmPassword')?.touched) {
    <mat-error>{{ 'auth.changePassword.passwordMismatch' | translate }}</mat-error>
  }
</mat-form-field>
```

Show the mismatch error only when `confirmPassword` is touched (user has left the field), to avoid showing it while typing. The `mat-error` is shown by Angular Material automatically when the parent `mat-form-field` is in an error state — but since `passwordsMismatch` is on the group (not on `confirmPassword` control), you need to conditionally render `mat-error` manually using `@if`.

**Alternative approach** — set the error directly on the `confirmPassword` control from the group validator. This is cleaner for Material's automatic error display:

```typescript
function passwordsMatchValidator(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get('newPassword')?.value;
    const confirmCtrl = group.get('confirmPassword');
    if (!confirmCtrl) { return null; }
    if (confirmCtrl.value && password !== confirmCtrl.value) {
      confirmCtrl.setErrors({ passwordsMismatch: true });
    } else if (confirmCtrl.hasError('passwordsMismatch')) {
      confirmCtrl.setErrors(null);
    }
    return null;
  };
}
```

With this approach, `mat-error` inside the `confirmPassword` mat-form-field is shown automatically by Angular Material (no manual `@if` needed).

Choose whichever approach is cleaner. The `setErrors` approach is recommended here for consistency with how Angular Material handles mat-error display.

### T1 — i18n keys to add

`en.json` — under `auth.changePassword`:
```json
"confirmPassword": "Confirm password",
"passwordMismatch": "Passwords do not match."
```

`fr.json` — under `auth.changePassword`:
```json
"confirmPassword": "Confirmer le mot de passe",
"passwordMismatch": "Les mots de passe ne correspondent pas."
```

### T2 — `ResetPasswordDialogComponent`

Place in: `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/`

This is a feature-specific dialog (not a shared utility), so it lives in the admin/users feature folder.

**Pattern to follow:** `confirm-dialog.component.ts` — same CDK Dialog injection pattern.

```typescript
// reset-password-dialog.component.ts
import { Component, inject } from '@angular/core';
import { A11yModule } from '@angular/cdk/a11y';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

export interface ResetPasswordDialogData {
  userName: string;
}

@Component({
  selector: 'app-reset-password-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe, A11yModule, MatFormFieldModule, MatInputModule],
  templateUrl: './reset-password-dialog.component.html',
  styleUrl: './reset-password-dialog.component.scss',
})
export class ResetPasswordDialogComponent {
  readonly dialogRef = inject<DialogRef<string>>(DialogRef);
  readonly data = inject<ResetPasswordDialogData>(DIALOG_DATA);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/.*[A-Z].*/), Validators.pattern(/.*[0-9].*/)]],
  });

  confirm(): void {
    if (this.form.invalid) { return; }
    this.dialogRef.close(this.form.getRawValue().newPassword);
  }

  cancel(): void {
    this.dialogRef.close(undefined);
  }
}
```

`DialogRef<string>` — the dialog returns a `string` (the new password) or `undefined` when cancelled.

**HTML (`reset-password-dialog.component.html`):**

```html
<div class="dialog">
  <h2 id="reset-dialog-title" class="dialog__title">{{ 'admin.users.resetDialog.title' | translate }}</h2>
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
      <button type="button" class="btn-ghost" (click)="cancel()">
        {{ 'admin.users.create.cancel' | translate }}
      </button>
      <button type="submit" class="btn-primary" [disabled]="form.invalid">
        {{ 'admin.users.actions.confirmReset' | translate }}
      </button>
    </div>
  </form>
</div>
```

- DO NOT add `cdkTrapFocus` / `cdkTrapFocusAutoCapture` on the container — CDK Dialog already installs a focus trap internally. A nested trap causes unexpected Tab-cycling. Follow the `confirm-dialog.component.html` pattern: no trap directive on the container, just `cdkFocusInitial` on the initial focus target.
- `cdkFocusInitial` on the password input — initial focus goes to the input, not the cancel button (this is a reset dialog where the user intends to type a password; unlike ConfirmDialog where cancel gets `cdkFocusInitial`). Escape key handling is built into CDK Dialog when `disableClose: false`.
- Reuse `'admin.users.create.password'` and `'admin.users.actions.confirmReset'` i18n keys already in `en.json`/`fr.json`

**SCSS (`reset-password-dialog.component.scss`):**

```scss
.dialog {
  padding: var(--pb-space-lg);
  min-width: 360px;
  max-width: 480px;

  &__title {
    margin: 0 0 var(--pb-space-sm);
    font: 600 18px/1.3 'DM Sans', sans-serif;
    color: var(--mat-sys-on-surface);
  }

  &__desc {
    margin: 0 0 var(--pb-space-md);
    font-size: 14px;
    color: var(--mat-sys-on-surface-variant);
  }

  &__field {
    width: 100%;
    margin-bottom: var(--pb-space-md);
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: var(--pb-space-sm);
  }
}
```

Reuse global CSS design tokens from `styles.scss` (defined by Story 1.7): `--pb-space-lg`, `--pb-space-md`, `--pb-space-sm`, `--mat-sys-*`. Do NOT import or duplicate these values.

**CDK Dialog open config for the dialog (in UserListComponent):**

```typescript
this.dialog.open<string, ResetPasswordDialogData, ResetPasswordDialogComponent>(
  ResetPasswordDialogComponent,
  {
    data: { userName: `${user.firstName} ${user.lastName}` },
    hasBackdrop: true,
    backdropClass: 'dialog-backdrop',   // defined in styles.scss by Story 1.8
    panelClass: 'dialog-panel',
    disableClose: false,
    ariaLabelledBy: 'reset-dialog-title',
    ariaDescribedBy: 'reset-dialog-desc',
  }
);
```

### T3 — `UserListComponent` rewrite

**Signals / properties to REMOVE:**
- `resetPasswordFor = signal<number | null>(null)`
- `resetPasswordForm = this.fb.nonNullable.group({ newPassword: [...] })`
- `showResetPassword(userId: number)`
- `cancelResetPassword()`
- `submitResetPassword(userId: number)` → replaced by `openResetPasswordDialog(user: UserDto)`

**New method:**

```typescript
import { Dialog } from '@angular/cdk/dialog';
import { firstValueFrom } from 'rxjs';
import { ResetPasswordDialogComponent, ResetPasswordDialogData } from './reset-password-dialog/reset-password-dialog.component';

// in class:
private readonly dialog = inject(Dialog);

openResetPasswordDialog(user: UserDto): void {
  const ref = this.dialog.open<string, ResetPasswordDialogData, ResetPasswordDialogComponent>(
    ResetPasswordDialogComponent,
    {
      data: { userName: `${user.firstName} ${user.lastName}` },
      hasBackdrop: true,
      backdropClass: 'dialog-backdrop',
      panelClass: 'dialog-panel',
      disableClose: false,
      ariaLabelledBy: 'reset-dialog-title',
      ariaDescribedBy: 'reset-dialog-desc',
    }
  );
  ref.closed.subscribe(async (newPassword) => {
    if (!newPassword) { return; }
    this.submitting.set(true);
    try {
      await firstValueFrom(this.userService.resetPassword(user.id, newPassword));
      this.toast.showSuccess(this.translate.instant('admin.users.success.resetPassword'));
    } catch {
      this.toast.showError(this.translate.instant('admin.users.error.resetPassword'));
    } finally {
      this.submitting.set(false);
    }
  });
}
```

Keep `submitting` signal — it's used to disable the table action buttons while the API call is in flight (prevent double-clicks).

**`FormBuilder` may be removed** from imports if `resetPasswordForm` is the only usage — check if it's still needed after the rewrite.

**HTML change** — replace the entire `@if / @else` block (lines 60–79 of current `user-list.component.html`) with a single button:

```html
<button type="button" class="btn-ghost" (click)="openResetPasswordDialog(user)" [disabled]="submitting()">
  {{ 'admin.users.actions.resetPassword' | translate }}
</button>
```

**Imports to ADD to `user-list.component.ts`:**
- `Dialog` from `@angular/cdk/dialog`
- `ResetPasswordDialogComponent` from `./reset-password-dialog/reset-password-dialog.component`

**Imports to REMOVE from `user-list.component.ts`** (if no longer used):
- `FormBuilder`, `ReactiveFormsModule`, `Validators` — only needed if form is still present. After removing `resetPasswordForm`, these can be removed.

### T2 — New i18n keys for the dialog

Add under `admin.users.resetDialog` in both JSON files:

`en.json`:
```json
"resetDialog": {
  "title": "Reset password",
  "description": "Set a new temporary password for {{ name }}."
}
```

`fr.json`:
```json
"resetDialog": {
  "title": "Réinitialiser le mot de passe",
  "description": "Définissez un nouveau mot de passe temporaire pour {{ name }}."
}
```

Note: `{{ name }}` is ngx-translate interpolation syntax — `TranslateService.instant('key', { name: 'value' })` or `| translate: { name: ... }` in templates.

### Existing Code NOT to Break

- `ChangePasswordComponent.onSubmit()` — still uses `this.form.getRawValue().newPassword`. The form now has two controls but `onSubmit()` only needs `newPassword`. No change needed to the submit logic.
- `UserListComponent.toggleEnabled()` — unchanged.
- `UserListComponent.navigateToCreate()` — unchanged.
- The `user-list.component.scss` — no changes needed (the inline form's `.reset-form` / `.reset-input` classes can be removed if they exist, but don't touch other rules).

### Test Patterns

**`change-password.component.spec.ts` additions:**
```typescript
it('disables submit when passwords do not match', () => {
  component.form.controls.newPassword.setValue('Password1');
  component.form.controls.confirmPassword.setValue('Password2');
  expect(component.form.invalid).toBe(true);
  expect(component.form.hasError('passwordsMismatch')).toBe(true);
});

it('enables submit when passwords match and meet requirements', () => {
  component.form.controls.newPassword.setValue('Password1');
  component.form.controls.confirmPassword.setValue('Password1');
  expect(component.form.valid).toBe(true);
});
```

**`user-list.component.spec.ts` — replace inline form tests with:**

The Dialog service needs to be mocked. Pattern:
```typescript
const dialogMock = { open: vi.fn().mockReturnValue({ closed: of('NewPassword1') }) };

// in providers:
{ provide: Dialog, useValue: dialogMock }

// test — dialog invocation:
it('opens reset dialog on button click', async () => {
  component.openResetPasswordDialog(mockUser);
  expect(dialogMock.open).toHaveBeenCalledWith(ResetPasswordDialogComponent, expect.objectContaining({ data: { userName: 'Alice Smith' } }));
});

// test — full flow (requires awaiting async subscription callback):
it('shows success toast after dialog confirms password', async () => {
  component.openResetPasswordDialog(mockUser);
  await fixture.whenStable(); // wait for async subscribe callback to complete
  expect(userServiceMock.resetPassword).toHaveBeenCalledWith(mockUser.id, 'NewPassword1');
  expect(toastMock.showSuccess).toHaveBeenCalledOnce();
});

// test — cancel path: closed emits undefined → no API call
it('does not call resetPassword when dialog is cancelled', async () => {
  dialogMock.open.mockReturnValueOnce({ closed: of(undefined) });
  component.openResetPasswordDialog(mockUser);
  await fixture.whenStable();
  expect(userServiceMock.resetPassword).not.toHaveBeenCalled();
});
```

**IMPORTANT:** `openResetPasswordDialog()` uses `ref.closed.subscribe(async callback)`. The subscribe callback is invoked synchronously by `of(...)`, but the async body (the `firstValueFrom` call) completes asynchronously. Always call `await fixture.whenStable()` after invoking the method before asserting API calls or toasts.

**`reset-password-dialog.component.spec.ts`** — create following the same pattern as `confirm-dialog.component.spec.ts`:
- Mock `DIALOG_DATA` with a user name
- Mock `DialogRef`
- Test: confirm() with valid form closes with password
- Test: confirm() with invalid form does NOT close
- Test: cancel() closes with undefined

### Project Structure Notes

- **Files created:**
  - `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.ts`
  - `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.html`
  - `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.scss`
  - `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.spec.ts`
- **Files modified:**
  - `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.ts`
  - `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.html`
  - `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.spec.ts`
  - `pluribourse-frontend/src/app/features/admin/users/user-list.component.ts`
  - `pluribourse-frontend/src/app/features/admin/users/user-list.component.html`
  - `pluribourse-frontend/src/app/features/admin/users/user-list.component.spec.ts`
  - `pluribourse-frontend/public/i18n/en.json`
  - `pluribourse-frontend/public/i18n/fr.json`
- **No backend changes** — the API calls (`changePassword`, `resetPassword`) are identical; only the frontend interaction changes.

### References

- [Source: change-password.component.ts] — current form: single `newPassword` field; `onSubmit()` calls `auth.changePassword(newPassword)`
- [Source: user-list.component.ts] — inline form uses `resetPasswordFor` signal + `resetPasswordForm`; `submitResetPassword()` calls `userService.resetPassword(userId, newPassword)`
- [Source: user-list.component.html:60–79] — inline `@if / @else` block to replace
- [Source: confirm-dialog.component.ts] — CDK Dialog pattern: `DIALOG_DATA`, `DialogRef<T>`, `A11yModule`, `cdkTrapFocus`, `cdkInitialFocus`
- [Source: confirm-dialog.service.ts] — CDK Dialog open config: `backdropClass: 'dialog-backdrop'`, `panelClass: 'dialog-panel'`
- [Source: styles.scss] — `.dialog-backdrop` defined by Story 1.8; `--pb-space-*` tokens defined by Story 1.7
- [Source: en.json / fr.json] — existing reusable keys: `admin.users.create.password`, `admin.users.actions.confirmReset`, `admin.users.create.cancel`, `auth.changePassword.minLength`

### Review Findings

- [x] [Review][Patch] `setErrors(null)` in `passwordsMatchValidator` destroys `required` error on `confirmPassword` [`passwords-match.validator.ts`] — Fixed: reconstruct errors object excluding only `passwordsMismatch` key; added `confirmRequired` i18n key (en/fr) and separate `@if` mat-error blocks in template.
- [x] [Review][Patch] `mat-error` for `confirmPassword` shows "Passwords do not match" when field is simply empty [`change-password.component.html`] — Fixed: two conditional `@if mat-error` blocks — one for `required`, one for `passwordsMismatch`.
- [x] [Review][Patch] `ref.closed.subscribe()` has no lifecycle teardown [`user-list.component.ts`] — Fixed: `takeUntilDestroyed(this.destroyRef)` added to the pipe.
- [x] [Review][Patch] `if (!newPassword)` is a loose falsy check [`user-list.component.ts`] — Fixed: guard changed to `newPassword === undefined`.
- [x] [Review][Patch] Missing test coverage for pattern-violation cases in reset dialog spec [`reset-password-dialog.component.spec.ts`] — Fixed: added 2 tests (lowercase-only 8 chars; uppercase missing digit).
- [x] [Review][Defer] Multiple `Validators.pattern()` calls collide on same `pattern` error key — deferred, pre-existing — Both `pattern(/.*[A-Z]*/)` and `pattern(/.*[0-9]*/)` write under the `pattern` key; the second silently overwrites the first. Pre-existing in the codebase, not introduced by this story.
- [x] [Review][Defer] `mat-error` in reset dialog shows only minLength regardless of which pattern failed — deferred, pre-existing — Consistent with the existing `change-password` form behavior. No per-validator error messages were specified in AC2.
- [x] [Review][Defer] Shared `submitting` signal creates table-wide lock on all reset buttons — deferred, pre-existing — In the old design only the inline submit button was gated on `submitting()`. Now every reset button in the table is disabled while a reset is in flight. Acceptable for single-user-at-a-time admin workflows; not a regression severe enough to block.

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- Fixed import error: `AbstractControl`, `ValidationErrors`, `ValidatorFn` belong to `@angular/forms`, not `@angular/core`.

### Completion Notes List

- T1: Added `passwordsMatchValidator()` cross-field validator using the `setErrors` approach on `confirmPassword` control. The validator clears the `passwordsMismatch` error when the fields match again, enabling Angular Material's automatic `mat-error` display without manual `@if` in the template.
- T1: Added 2 new i18n keys in EN/FR under `auth.changePassword`: `confirmPassword` and `passwordMismatch`.
- T2: Created `ResetPasswordDialogComponent` following the exact CDK Dialog pattern from `confirm-dialog.component.ts`. Used `cdkFocusInitial` (not `cdkInitialFocus`) on the password input per Angular CDK spec.
- T2: Created `reset-password-dialog.component.spec.ts` with 7 tests covering confirm/cancel/invalid-form paths.
- T2: Added 2 new i18n keys under `admin.users.resetDialog` (EN/FR).
- T3: Removed `resetPasswordFor`, `resetPasswordForm`, `showResetPassword()`, `cancelResetPassword()`, `submitResetPassword()` from `UserListComponent`. Also removed `FormBuilder` and `ReactiveFormsModule` imports (no longer needed).
- T3: Replaced 20-line `@if/@else` inline form block in template with a single `<button>` calling `openResetPasswordDialog(user)`.
- T3: Removed dead `.reset-form` and `.reset-input` SCSS rules from `user-list.component.scss`.
- T3: Updated spec — removed 2 inline-form tests, added 4 new dialog/API tests (invocation, success, cancel, error).
- T4: 21 test files, 115 tests pass — zero regressions.

### File List

- `pluribourse-frontend/src/app/shared/validators/passwords-match.validator.ts` (new)
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.ts` (new)
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.html` (new)
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.scss` (new)
- `pluribourse-frontend/src/app/features/admin/users/reset-password-dialog/reset-password-dialog.component.spec.ts` (new)
- `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.ts` (modified)
- `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.html` (modified)
- `pluribourse-frontend/src/app/features/auth/change-password/change-password.component.spec.ts` (modified)
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.ts` (modified)
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.html` (modified)
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.scss` (modified)
- `pluribourse-frontend/src/app/features/admin/users/user-list.component.spec.ts` (modified)
- `pluribourse-frontend/public/i18n/en.json` (modified)
- `pluribourse-frontend/public/i18n/fr.json` (modified)
