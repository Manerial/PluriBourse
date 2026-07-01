import { FormControl } from '@angular/forms';
import { maxDecimalsValidator } from './financial.validators';

describe('maxDecimalsValidator', () => {
  it('accepts an integer value', () => {
    const control = new FormControl(20, maxDecimalsValidator(2));
    expect(control.errors).toBeNull();
  });

  it('accepts a value with up to the allowed number of decimals', () => {
    const control = new FormControl('20.5', maxDecimalsValidator(2));
    expect(control.errors).toBeNull();
    control.setValue('20.55');
    expect(control.errors).toBeNull();
  });

  it('rejects a value with more decimals than allowed', () => {
    const control = new FormControl('20.555', maxDecimalsValidator(2));
    expect(control.errors).toEqual({ maxDecimals: true });
  });

  it('rejects a non-numeric value', () => {
    const control = new FormControl('abc', maxDecimalsValidator(2));
    expect(control.errors).toEqual({ maxDecimals: true });
  });

  it('accepts a null value (delegates required-ness to another validator)', () => {
    const control = new FormControl(null, maxDecimalsValidator(2));
    expect(control.errors).toBeNull();
  });
});
