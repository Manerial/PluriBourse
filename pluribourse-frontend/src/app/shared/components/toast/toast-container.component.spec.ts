import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { signal } from '@angular/core';
import { ToastContainerComponent } from './toast-container.component';
import { ToastService, Toast } from './toast.service';

// A real matching route (rather than an empty provideRouter([])) is required so that
// clicking the link's rendered <a routerLink> below resolves an actual navigation instead of
// leaving an unhandled rejection once the test's injector is torn down.
@Component({ selector: 'app-print-queue-stub', template: '' })
class PrintQueueStubComponent {}

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
        provideRouter([{ path: 'admin/print-queue', component: PrintQueueStubComponent }]),
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

  it('an error toast with a link renders it with the right routerLink and label', () => {
    mockToastSignal.set({
      message: 'Some failed',
      type: 'error',
      link: { path: '/admin/print-queue', label: 'See the print queue' },
    });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    const link = fixture.nativeElement.querySelector('.toast__link');
    expect(link).not.toBeNull();
    expect(link.textContent).toContain('See the print queue');
    expect(link.getAttribute('href')).toBe('/admin/print-queue');
  });

  it('a toast without a link renders none', () => {
    mockToastSignal.set({ message: 'Printer offline', type: 'error' });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.toast__link')).toBeNull();
  });

  it('clicking the link calls toastService.close()', async () => {
    mockToastSignal.set({
      message: 'Some failed',
      type: 'error',
      link: { path: '/admin/print-queue', label: 'See the print queue' },
    });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.toast__link').click();
    expect(mockClose).toHaveBeenCalledOnce();
    await fixture.whenStable();
  });

  it('ctrl/cmd/middle-clicking the link opens it in a new tab without closing the toast', () => {
    mockToastSignal.set({
      message: 'Some failed',
      type: 'error',
      link: { path: '/admin/print-queue', label: 'See the print queue' },
    });
    const fixture = TestBed.createComponent(ToastContainerComponent);
    fixture.detectChanges();
    const link: HTMLAnchorElement = fixture.nativeElement.querySelector('.toast__link');

    link.dispatchEvent(new MouseEvent('click', { ctrlKey: true, cancelable: true }));
    link.dispatchEvent(new MouseEvent('click', { metaKey: true, cancelable: true }));
    link.dispatchEvent(new MouseEvent('click', { shiftKey: true, cancelable: true }));
    link.dispatchEvent(new MouseEvent('click', { button: 1, cancelable: true }));

    expect(mockClose).not.toHaveBeenCalled();
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
