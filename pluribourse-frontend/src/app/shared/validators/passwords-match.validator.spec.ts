import { FormControl, FormGroup, Validators } from '@angular/forms';
import { passwordsMatchValidator } from './passwords-match.validator';

describe('passwordsMatchValidator', () => {
  function buildGroup(password: string, confirmPassword: string): FormGroup {
    const group = new FormGroup({
      password: new FormControl(password),
      confirmPassword: new FormControl(confirmPassword),
    });
    group.setValidators(passwordsMatchValidator('password', 'confirmPassword'));
    group.updateValueAndValidity();
    return group;
  }

  it('sets no error on the confirm control when passwords match', () => {
    const group = buildGroup('Password1', 'Password1');
    expect(group.get('confirmPassword')?.errors).toBeNull();
  });

  it('sets passwordsMismatch on the confirm control when passwords differ', () => {
    const group = buildGroup('Password1', 'Password2');
    expect(group.get('confirmPassword')?.errors).toEqual({ passwordsMismatch: true });
  });

  it('sets no error when the confirm control is empty', () => {
    const group = buildGroup('Password1', '');
    expect(group.get('confirmPassword')?.errors).toBeNull();
  });

  it('always returns null at the group level (errors are set on the confirm control)', () => {
    const group = buildGroup('Password1', 'Password2');
    expect(group.errors).toBeNull();
  });

  it('merges passwordsMismatch with the confirm control own validation errors', () => {
    // confirmPassword keeps its own Validators.required, as real forms do (see change-password.component.ts).
    const group = new FormGroup({
      password: new FormControl('Password1'),
      confirmPassword: new FormControl('', Validators.required),
    });
    group.setValidators(passwordsMatchValidator('password', 'confirmPassword'));
    group.updateValueAndValidity();

    // Empty confirm: passwordsMismatch is not evaluated (falsy value), only the field's own required error shows.
    expect(group.get('confirmPassword')?.errors).toEqual({ required: true });

    group.get('confirmPassword')?.setValue('Mismatch1');
    expect(group.get('confirmPassword')?.errors).toEqual({ passwordsMismatch: true });
  });

  it('clears passwordsMismatch (while keeping the confirm control own errors) once the password field is edited to match', () => {
    // Editing confirmPassword itself always re-runs its own validators first, which would already
    // reset passwordsMismatch before the group validator runs. The only way this validator can
    // observe and clear a stale passwordsMismatch is when the *password* field changes instead,
    // since editing it does not touch confirmPassword's own validators.
    const group = new FormGroup({
      password: new FormControl('Password1'),
      confirmPassword: new FormControl('LongMismatch', Validators.maxLength(5)),
    });
    group.setValidators(passwordsMatchValidator('password', 'confirmPassword'));
    group.updateValueAndValidity();

    const confirmCtrl = group.get('confirmPassword')!;
    expect(confirmCtrl.errors).toEqual({ maxlength: { requiredLength: 5, actualLength: 12 }, passwordsMismatch: true });

    group.get('password')?.setValue('LongMismatch');

    expect(confirmCtrl.errors).toEqual({ maxlength: { requiredLength: 5, actualLength: 12 } });
  });
});
