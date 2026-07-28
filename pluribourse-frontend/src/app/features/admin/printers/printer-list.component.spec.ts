import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { Dialog } from '@angular/cdk/dialog';
import { PrinterListComponent } from './printer-list.component';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { DiscoveredPrinter, IgnoredPrinter, PrinterSummary, PrintResult } from '../../../models/printer-registry.model';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { PrinterFormComponent } from './printer-form.component';

const MOCK_PRINTERS: PrinterSummary[] = [
  { id: 1, name: 'Guichet Thermique', type: 'THERMAL', connected: true },
  { id: 2, name: 'Guichet A4', type: 'A4', connected: false },
];

const MOCK_DISCOVERED: DiscoveredPrinter[] = [
  { printerBridgeId: 'bridge-thermal-1', name: 'Zebra ZQ320', type: 'THERMAL', status: 'ONLINE' },
];

const MOCK_IGNORED: IgnoredPrinter[] = [
  { printerBridgeId: 'bridge-ignored-1', name: 'Imprimante Voisin', ignoredAt: '2026-07-28' },
];

describe('PrinterListComponent', () => {
  let fixture: ComponentFixture<PrinterListComponent>;
  let component: PrinterListComponent;

  const printerRegistryServiceMock = {
    list: vi.fn().mockReturnValue(of(MOCK_PRINTERS)),
    delete: vi.fn().mockReturnValue(of(undefined)),
    testPrint: vi.fn().mockReturnValue(of({ status: 'OK', message: null } satisfies PrintResult)),
    discover: vi.fn().mockReturnValue(of(MOCK_DISCOVERED)),
    listIgnored: vi.fn().mockReturnValue(of(MOCK_IGNORED)),
    reactivate: vi.fn().mockReturnValue(of(undefined)),
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
    printerRegistryServiceMock.testPrint.mockReturnValue(of({ status: 'OK', message: null } satisfies PrintResult));
    printerRegistryServiceMock.discover.mockReturnValue(of(MOCK_DISCOVERED));
    printerRegistryServiceMock.listIgnored.mockReturnValue(of(MOCK_IGNORED));
    printerRegistryServiceMock.reactivate.mockReturnValue(of(undefined));
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

  it('openCreateDialog waits for discovery before opening PrinterFormComponent', async () => {
    await component.openCreateDialog();
    expect(printerRegistryServiceMock.discover).toHaveBeenCalledTimes(1);
    expect(dialogMock.open).toHaveBeenCalledWith(
      PrinterFormComponent,
      expect.objectContaining({ data: { discoveredPrinters: MOCK_DISCOVERED, discoveryError: false } })
    );
    expect(component.discovering()).toBe(false);
  });

  it('openCreateDialog still opens the dialog with a discovery error flag when discover() fails', async () => {
    printerRegistryServiceMock.discover.mockReturnValueOnce(throwError(() => new Error('503')));
    await component.openCreateDialog();
    expect(dialogMock.open).toHaveBeenCalledWith(
      PrinterFormComponent,
      expect.objectContaining({ data: { discoveredPrinters: [], discoveryError: true } })
    );
  });

  it('reloads the printer list after the create dialog closes', async () => {
    printerRegistryServiceMock.list.mockClear();
    await component.openCreateDialog();
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

  it('testPrint shows a success toast and clears the testing state on a successful result', async () => {
    printerRegistryServiceMock.testPrint.mockReturnValueOnce(of({ status: 'OK', message: null } satisfies PrintResult));
    await component.testPrint(MOCK_PRINTERS[0]);
    expect(printerRegistryServiceMock.testPrint).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(toastMock.showError).not.toHaveBeenCalled();
    expect(component.testingId()).toBeNull();
  });

  it('testPrint shows the error message from an ERROR result', async () => {
    printerRegistryServiceMock.testPrint.mockReturnValueOnce(of({ status: 'ERROR', message: 'bourrage papier' } satisfies PrintResult));
    await component.testPrint(MOCK_PRINTERS[0]);
    expect(toastMock.showError).toHaveBeenCalledWith('bourrage papier');
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
  });

  it('testPrint shows a generic error toast when the call itself fails', async () => {
    printerRegistryServiceMock.testPrint.mockReturnValueOnce(throwError(() => new Error('network')));
    await component.testPrint(MOCK_PRINTERS[0]);
    expect(toastMock.showError).toHaveBeenCalledWith('admin.printers.error.testPrint');
    expect(component.testingId()).toBeNull();
  });

  it('loads the ignored printers section on init', () => {
    expect(printerRegistryServiceMock.listIgnored).toHaveBeenCalledTimes(1);
    expect(component.ignoredPrinters()).toEqual(MOCK_IGNORED);
  });

  it('a failure loading ignored printers does not affect the main printer list', async () => {
    printerRegistryServiceMock.listIgnored.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBeNull();
    expect(component.printers().length).toBe(2);
    expect(component.ignoredPrinters()).toEqual([]);
  });

  it('reactivate removes the printer from the ignored list and shows a success toast', async () => {
    await component.reactivate(MOCK_IGNORED[0]);
    expect(printerRegistryServiceMock.reactivate).toHaveBeenCalledWith('bridge-ignored-1');
    expect(component.ignoredPrinters()).toEqual([]);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('reactivate shows an error toast when the call fails', async () => {
    printerRegistryServiceMock.reactivate.mockReturnValueOnce(throwError(() => new Error('server')));
    await component.reactivate(MOCK_IGNORED[0]);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(component.ignoredPrinters()).toEqual(MOCK_IGNORED);
  });
});
