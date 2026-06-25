import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { ToastContainerComponent } from './toast-container.component';
import { ToastService, Toast } from './toast.service';

describe('ToastContainerComponent', () => {
  const mockToastSignal = signal<Toast | null>(null);
  const mockClose = vi.fn();
  const mockToastService = {
    toast: mockToastSignal.asReadonly(),
    close: mockClose,
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [ToastContainerComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: ToastService, useValue: mockToastService },
      ],
    }).compileComponents();
  });

  it('renders nothing when toast is null', () => {
    mockToastSignal.set(null);
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.toast')).toBeNull();
  });

  it('renders success toast with message', () => {
    mockToastSignal.set({ message: 'Item saved', type: 'success' });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    const toast = fixture.nativeElement.querySelector('.toast');
    expect(toast).not.toBeNull();
    expect(toast.classList).toContain('toast--success');
    expect(fixture.nativeElement.querySelector('.toast__message').textContent).toContain('Item saved');
  });

  it('renders error toast with close button', () => {
    mockToastSignal.set({ message: 'Printer offline', type: 'error' });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    const toast = fixture.nativeElement.querySelector('.toast');
    expect(toast.classList).toContain('toast--error');
    expect(fixture.nativeElement.querySelector('.toast__close')).not.toBeNull();
  });

  it('success toast has no close button', () => {
    mockToastSignal.set({ message: 'Done', type: 'success' });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.toast__close')).toBeNull();
  });

  it('close button calls toastService.close()', () => {
    mockToastSignal.set({ message: 'Error', type: 'error' });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.toast__close').click();
    expect(mockClose).toHaveBeenCalledOnce();
  });

  it('error toast has role=alert', () => {
    mockToastSignal.set({ message: 'Err', type: 'error' });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.toast').getAttribute('role')).toBe('alert');
  });

  it('success toast has role=status', () => {
    mockToastSignal.set({ message: 'OK', type: 'success' });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.toast').getAttribute('role')).toBe('status');
  });
});
