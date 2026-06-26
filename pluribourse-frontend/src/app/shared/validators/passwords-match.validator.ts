import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export function passwordsMatchValidator(passwordField: string, confirmField: string): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordField)?.value;
    const confirmCtrl = group.get(confirmField);
    if (!confirmCtrl) {
      return null;
    }
    if (confirmCtrl.value && password !== confirmCtrl.value) {
      confirmCtrl.setErrors({ ...(confirmCtrl.errors || {}), passwordsMismatch: true });
    } else if (confirmCtrl.hasError('passwordsMismatch')) {
      const errors = { ...confirmCtrl.errors };
      delete errors['passwordsMismatch'];
      confirmCtrl.setErrors(Object.keys(errors).length ? errors : null);
    }
    return null;
  };
}
