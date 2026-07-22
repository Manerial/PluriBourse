import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Dialog } from '@angular/cdk/dialog';
import { PrinterListComponent } from './printer-list.component';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { PrinterSummary } from '../../../models/printer-registry.model';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { PrinterFormComponent } from './printer-form.component';

const MOCK_PRINTERS: PrinterSummary[] = [
  { id: 1, name: 'Guichet Thermique', type: 'THERMAL', connected: true },
  { id: 2, name: 'Guichet A4', type: 'A4', connected: false },
];

describe('PrinterListComponent', () => {
  let fixture: ComponentFixture<PrinterListComponent>;
  let component: PrinterListComponent;

  const printerRegistryServiceMock = {
    list: vi.fn().mockReturnValue(of(MOCK_PRINTERS)),
    delete: vi.fn().mockReturnValue(of(undefined)),
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  const dialogMock = {
    open: vi.fn().mockReturnValue({ closed: of(undefined) })
  };

  const confirmDialogMock = {
    open: vi.fn().mockReturnValue(of(false))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    printerRegistryServiceMock.list.mockReturnValue(of(MOCK_PRINTERS));
    dialogMock.open.mockReturnValue({ closed: of(undefined) });
    confirmDialogMock.open.mockReturnValue(of(false));

    await TestBed.configureTestingModule({
      imports: [PrinterListComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: PrinterRegistryService, useValue: printerRegistryServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: Dialog, useValue: dialogMock },
        { provide: ConfirmDialogService, useValue: confirmDialogMock },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PrinterListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders the printer list on init', () => {
    expect(printerRegistryServiceMock.list).toHaveBeenCalledTimes(1);
    expect(component.printers().length).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('shows error key when load fails', async () => {
    printerRegistryServiceMock.list.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('admin.printers.error.load');
  });

  it('shows an empty state when the registry is empty', async () => {
    printerRegistryServiceMock.list.mockReturnValue(of([]));
    fixture = TestBed.createComponent(PrinterListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    expect(component.printers().length).toBe(0);
  });

  it('openCreateDialog opens PrinterFormComponent', () => {
    component.openCreateDialog();
    expect(dialogMock.open).toHaveBeenCalledWith(PrinterFormComponent, expect.anything());
  });

  it('reloads the printer list after the create dialog closes', async () => {
    printerRegistryServiceMock.list.mockClear();
    component.openCreateDialog();
    await fixture.whenStable();
    expect(printerRegistryServiceMock.list).toHaveBeenCalledTimes(1);
  });

  it('does not call delete when the confirm dialog is cancelled', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(false));
    await component.confirmDelete(MOCK_PRINTERS[0]);
    expect(printerRegistryServiceMock.delete).not.toHaveBeenCalled();
  });

  it('calls delete and removes the printer from the list after confirm', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(true));
    await component.confirmDelete(MOCK_PRINTERS[0]);
    expect(printerRegistryServiceMock.delete).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.printers().find(p => p.id === 1)).toBeUndefined();
    expect(component.submitting()).toBe(false);
  });

  it('shows error toast when delete fails', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(true));
    printerRegistryServiceMock.delete.mockReturnValueOnce(throwError(() => new Error('server')));
    await component.confirmDelete(MOCK_PRINTERS[0]);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });
});
