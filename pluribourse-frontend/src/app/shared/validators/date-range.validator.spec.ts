import { FormControl, FormGroup } from '@angular/forms';
import { dateRangeValidator } from './date-range.validator';

describe('dateRangeValidator', () => {
  function buildGroup(start: Date | null, end: Date | null): FormGroup {
    return new FormGroup(
      {
        startDate: new FormControl(start),
        endDate: new FormControl(end),
      },
      { validators: [dateRangeValidator('startDate', 'endDate')] },
    );
  }

  it('is valid when endDate is after startDate', () => {
    const group = buildGroup(new Date(2026, 0, 1), new Date(2026, 0, 3));
    expect(group.errors).toBeNull();
  });

  it('is valid when endDate equals startDate', () => {
    const group = buildGroup(new Date(2026, 0, 1), new Date(2026, 0, 1));
    expect(group.errors).toBeNull();
  });

  it('is invalid when endDate is before startDate', () => {
    const group = buildGroup(new Date(2026, 0, 3), new Date(2026, 0, 1));
    expect(group.errors).toEqual({ dateRange: true });
  });

  it('is valid when either date is missing', () => {
    expect(buildGroup(null, new Date(2026, 0, 1)).errors).toBeNull();
    expect(buildGroup(new Date(2026, 0, 1), null).errors).toBeNull();
  });
});
