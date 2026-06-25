import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
    vi.useFakeTimers();
  });

  afterEach(() => vi.useRealTimers());

  it('showSuccess sets toast with type success', () => {
    service.showSuccess('Saved!');
    expect(service.toast()).toEqual({ message: 'Saved!', type: 'success' });
  });

  it('showSuccess auto-dismisses after 4s', () => {
    service.showSuccess('Saved!');
    vi.advanceTimersByTime(4000);
    expect(service.toast()).toBeNull();
  });

  it('showError sets toast with type error and does not auto-dismiss', () => {
    service.showError('Printer offline');
    vi.advanceTimersByTime(10000);
    expect(service.toast()).toEqual({ message: 'Printer offline', type: 'error' });
  });

  it('close() clears toast immediately', () => {
    service.showError('Printer offline');
    service.close();
    expect(service.toast()).toBeNull();
  });

  it('showSuccess replaces previous toast', () => {
    service.showError('Error 1');
    service.showSuccess('Success');
    expect(service.toast()?.type).toBe('success');
  });

  it('initial toast is null', () => {
    expect(service.toast()).toBeNull();
  });
});
