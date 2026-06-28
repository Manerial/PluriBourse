import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { vi } from 'vitest';
import {
  UpdateCommissionRateDialogComponent,
  UpdateCommissionRateDialogData
} from './update-commission-rate-dialog.component';

const testData: UpdateCommissionRateDialogData = { editionId: 1, currentRate: 20 };

describe('UpdateCommissionRateDialogComponent', () => {
  const mockClose = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [UpdateCommissionRateDialogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: { close: mockClose } },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('initializes form with currentRate', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.form.getRawValue().commissionRate).toBe(20);
  });

  it('confirm() with valid rate closes dialog with the new rate', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.commissionRate.setValue(15);
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith(15);
  });

  it('confirm() with null rate does NOT close dialog', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.commissionRate.setValue(null as unknown as number);
    fixture.componentInstance.confirm();
    expect(mockClose).not.toHaveBeenCalled();
  });

  it('cancel() closes dialog with undefined', () => {
    const fixture = TestBed.createComponent(UpdateCommissionRateDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(undefined);
  });
});
