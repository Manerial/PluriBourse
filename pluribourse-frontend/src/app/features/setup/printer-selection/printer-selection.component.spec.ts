import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AuthService } from '../../../services/auth.service';
import { PrintService } from '../../../services/print.service';
import { PrinterSelectionComponent } from './printer-selection.component';

describe('PrinterSelectionComponent', () => {
  const mockPrintService = {
    getAvailablePrinters: vi.fn(),
    submitSelection: vi.fn(),
  };

  const mockAuthService = {
    currentUser: vi.fn().mockReturnValue({ role: 'VOLUNTEER' }),
  };

  let fixture: ReturnType<typeof TestBed.createComponent<PrinterSelectionComponent>>;
  let component: PrinterSelectionComponent;
  let router: Router;

  beforeEach(async () => {
    vi.clearAllMocks();
    mockAuthService.currentUser.mockReturnValue({ role: 'VOLUNTEER' });
    mockPrintService.getAvailablePrinters.mockReturnValue(
      of([
        { id: 1, name: 'Thermique Guichet', type: 'THERMAL' },
        { id: 2, name: 'A4 Guichet', type: 'A4' },
      ])
    );
    mockPrintService.submitSelection.mockReturnValue(of({ done: true, thermalPrinterId: 1, a4PrinterId: 2 }));

    await TestBed.configureTestingModule({
      imports: [PrinterSelectionComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        provideRouter([]),
        provideAnimationsAsync(),
        { provide: PrintService, useValue: mockPrintService },
        { provide: AuthService, useValue: mockAuthService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PrinterSelectionComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('splits available printers by type', () => {
    expect(component.thermalPrinters()).toEqual([{ id: 1, name: 'Thermique Guichet', type: 'THERMAL' }]);
    expect(component.a4Printers()).toEqual([{ id: 2, name: 'A4 Guichet', type: 'A4' }]);
  });

  it('shows a warning when a printer type has no available printer', async () => {
    mockPrintService.getAvailablePrinters.mockReturnValue(of([]));
    const emptyFixture = TestBed.createComponent(PrinterSelectionComponent);
    emptyFixture.detectChanges();
    await emptyFixture.whenStable();
    expect(emptyFixture.nativeElement.querySelectorAll('.notification').length).toBeGreaterThan(0);
  });

  it('submit button is never disabled by form validity', () => {
    const btn: HTMLButtonElement = fixture.nativeElement.querySelector('button[type="submit"]');
    expect(component.form.valid).toBe(true);
    expect(btn.disabled).toBe(false);
  });

  it('submits the selection and navigates to /volunteer for a volunteer', async () => {
    component.form.setValue({ thermalPrinterId: 1, a4PrinterId: 2 });
    await component.onSubmit();
    expect(mockPrintService.submitSelection).toHaveBeenCalledWith(1, 2);
    expect(router.navigate).toHaveBeenCalledWith(['/volunteer']);
  });

  it('submits the selection and navigates to /admin for an admin', async () => {
    mockAuthService.currentUser.mockReturnValue({ role: 'ADMIN' });
    component.form.setValue({ thermalPrinterId: 1, a4PrinterId: 2 });
    await component.onSubmit();
    expect(mockPrintService.submitSelection).toHaveBeenCalledWith(1, 2);
    expect(router.navigate).toHaveBeenCalledWith(['/admin']);
  });

  it('submits with both selections empty', async () => {
    await component.onSubmit();
    expect(mockPrintService.submitSelection).toHaveBeenCalledWith(null, null);
  });

  it('shows an error and leaves both lists empty when fetching printers fails', async () => {
    mockPrintService.getAvailablePrinters.mockReturnValue(throwError(() => new Error('network down')));
    const failingFixture = TestBed.createComponent(PrinterSelectionComponent);
    failingFixture.detectChanges();
    await failingFixture.whenStable();
    expect(failingFixture.componentInstance.error()).toBe(true);
    expect(failingFixture.componentInstance.thermalPrinters()).toEqual([]);
    expect(failingFixture.componentInstance.a4Printers()).toEqual([]);
  });

  it('shows an error and does not navigate when submit fails', async () => {
    mockPrintService.submitSelection.mockReturnValue(throwError(() => new Error('printer unavailable')));
    await component.onSubmit();
    expect(component.error()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(component.loading()).toBe(false);
  });

  it('clears a previous error on the next submit attempt', async () => {
    mockPrintService.submitSelection.mockReturnValueOnce(throwError(() => new Error('printer unavailable')));
    await component.onSubmit();
    expect(component.error()).toBe(true);

    mockPrintService.submitSelection.mockReturnValue(of({ done: true, thermalPrinterId: 1, a4PrinterId: 2 }));
    await component.onSubmit();
    expect(component.error()).toBe(false);
  });
});
