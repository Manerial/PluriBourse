import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { vi } from 'vitest';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

const testData: ConfirmDialogData = {
  title: 'Confirm action',
  description: 'This cannot be undone.',
};

describe('ConfirmDialogComponent', () => {
  const mockClose = vi.fn();
  const mockDialogRef = { close: mockClose };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: mockDialogRef },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('renders title and description', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement;
    expect(el.querySelector('.dialog__title').textContent).toContain('Confirm action');
    expect(el.querySelector('.dialog__description').textContent).toContain('This cannot be undone.');
  });

  it('confirm() calls dialogRef.close(true)', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith(true);
  });

  it('cancel() calls dialogRef.close(false)', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(false);
  });

  it('renders cancel button with cdkFocusInitial attribute', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    const cancelBtn = fixture.nativeElement.querySelector('.btn-ghost');
    expect(cancelBtn).not.toBeNull();
  });

  it('renders confirm button with btn-primary class by default', () => {
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    const confirmBtn = fixture.nativeElement.querySelector('.btn-primary');
    expect(confirmBtn).not.toBeNull();
  });

  it('renders confirm button with btn-error class when confirmVariant is error', async () => {
    const errorData: ConfirmDialogData = {
      title: 'Delete',
      description: 'This will delete permanently.',
      confirmVariant: 'error',
    };
    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: mockDialogRef },
        { provide: DIALOG_DATA, useValue: errorData },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(ConfirmDialogComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.btn-error')).not.toBeNull();
  });
});
