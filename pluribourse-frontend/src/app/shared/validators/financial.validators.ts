import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function maxDecimalsValidator(max: number): ValidatorFn {
  const pattern = new RegExp(`^\\d+(\\.\\d{1,${max}})?$`);
  return (control: AbstractControl): ValidationErrors | null => {
    if (control.value == null) {
      return null;
    }
    return pattern.test(String(control.value)) ? null : { maxDecimals: true };
  };
}
