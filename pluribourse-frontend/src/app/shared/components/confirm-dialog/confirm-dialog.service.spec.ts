import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { Dialog } from '@angular/cdk/dialog';
import { of } from 'rxjs';
import { ConfirmDialogService } from './confirm-dialog.service';

describe('ConfirmDialogService', () => {
  const mockDialogRef = { closed: of(true) };
  const mockDialog = { open: vi.fn().mockReturnValue(mockDialogRef) };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.configureTestingModule({
      providers: [
        ConfirmDialogService,
        { provide: Dialog, useValue: mockDialog },
      ],
    });
  });

  it('opens dialog with provided data', () => {
    const service = TestBed.inject(ConfirmDialogService);
    service.open({ title: 'Test', description: 'Test desc' });
    expect(mockDialog.open).toHaveBeenCalledOnce();
    const callArgs = mockDialog.open.mock.calls[0][1];
    expect(callArgs.data).toEqual({ title: 'Test', description: 'Test desc' });
  });

  it('returns closed observable from dialog ref', () => {
    const service = TestBed.inject(ConfirmDialogService);
    const result$ = service.open({ title: 'Test', description: 'Test desc' });
    let emitted: boolean | undefined;
    result$.subscribe(v => { emitted = v as boolean; });
    expect(emitted).toBe(true);
  });

  it('opens with backdrop enabled', () => {
    const service = TestBed.inject(ConfirmDialogService);
    service.open({ title: 'T', description: 'D' });
    const callArgs = mockDialog.open.mock.calls[0][1];
    expect(callArgs.hasBackdrop).toBe(true);
    expect(callArgs.backdropClass).toBe('dialog-backdrop');
  });
});
