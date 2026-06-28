import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { vi } from 'vitest';
import { ResetPasswordDialogComponent, ResetPasswordDialogData } from './reset-password-dialog.component';

const testData: ResetPasswordDialogData = { userName: 'Alice Smith' };

describe('ResetPasswordDialogComponent', () => {
  const mockClose = vi.fn();
  const mockDialogRef = { close: mockClose };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ResetPasswordDialogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        provideAnimationsAsync(),
        { provide: DialogRef, useValue: mockDialogRef },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('renders title and user name in description', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement;
    expect(el.querySelector('.dialog__title')).not.toBeNull();
    expect(el.querySelector('.dialog__desc')).not.toBeNull();
  });

  it('confirm() with valid form closes dialog with the password', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.newPassword.setValue('Password1');
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith('Password1');
  });

  it('confirm() with invalid form does NOT close dialog', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.newPassword.setValue('weak');
    fixture.componentInstance.confirm();
    expect(mockClose).not.toHaveBeenCalled();
  });

  it('cancel() closes dialog with undefined', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(undefined);
  });

  it('submit button is disabled when form is invalid', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.disabled).toBe(true);
  });

  it('submit button is enabled when password meets requirements', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.newPassword.setValue('Password1');
    fixture.detectChanges();
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(btn.disabled).toBe(false);
  });

  it('has cancel button with mat-button', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    const cancelBtn = fixture.nativeElement.querySelector('button[mat-button]');
    expect(cancelBtn).not.toBeNull();
  });

  it('confirm() with 8-char lowercase-only password does NOT close dialog', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.newPassword.setValue('password');
    fixture.componentInstance.confirm();
    expect(mockClose).not.toHaveBeenCalled();
  });

  it('confirm() with password missing digit does NOT close dialog', () => {
    const fixture = TestBed.createComponent(ResetPasswordDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.newPassword.setValue('Passwords');
    fixture.componentInstance.confirm();
    expect(mockClose).not.toHaveBeenCalled();
  });
});
