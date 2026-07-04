import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function dateRangeValidator(startControlName: string, endControlName: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const start = group.get(startControlName)?.value as Date | null;
    const end = group.get(endControlName)?.value as Date | null;
    if (!start || !end) {
      return null;
    }
    return end.getTime() < start.getTime() ? { dateRange: true } : null;
  };
}
