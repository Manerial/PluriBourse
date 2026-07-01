import { FormControl } from '@angular/forms';
import { hasDigit, hasUppercase, passwordStrengthValidators } from './password-strength.validator';

describe('hasUppercase', () => {
  it('rejects a value without an uppercase letter', () => {
    const control = new FormControl('lowercase1', hasUppercase());
    expect(control.errors).toEqual({ needsUppercase: true });
  });

  it('accepts a value with an uppercase letter', () => {
    const control = new FormControl('Lowercase1', hasUppercase());
    expect(control.errors).toBeNull();
  });

  it('rejects an empty value', () => {
    const control = new FormControl('', hasUppercase());
    expect(control.errors).toEqual({ needsUppercase: true });
  });
});

describe('hasDigit', () => {
  it('rejects a value without a digit', () => {
    const control = new FormControl('NoDigitsHere', hasDigit());
    expect(control.errors).toEqual({ needsDigit: true });
  });

  it('accepts a value with a digit', () => {
    const control = new FormControl('HasDigit1', hasDigit());
    expect(control.errors).toBeNull();
  });
});

describe('passwordStrengthValidators', () => {
  it('rejects a password that is too short, uppercase-less and digit-less', () => {
    const control = new FormControl('short', passwordStrengthValidators);
    expect(control.errors).toEqual({ minlength: expect.anything(), needsUppercase: true, needsDigit: true });
  });

  it('accepts a password satisfying length, uppercase and digit requirements', () => {
    const control = new FormControl('Password1', passwordStrengthValidators);
    expect(control.errors).toBeNull();
  });
});
