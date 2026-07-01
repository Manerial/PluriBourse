import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

export function hasUppercase(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    /[A-Z]/.test(control.value ?? '') ? null : { needsUppercase: true };
}

export function hasDigit(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    /[0-9]/.test(control.value ?? '') ? null : { needsDigit: true };
}

export const passwordStrengthValidators: ValidatorFn[] = [
  Validators.minLength(8),
  hasUppercase(),
  hasDigit(),
];
