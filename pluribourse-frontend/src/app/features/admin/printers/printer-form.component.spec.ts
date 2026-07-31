import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PrinterFormComponent, PrinterFormDialogData } from './printer-form.component';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { DiscoveredPrinter } from '../../../models/printer-registry.model';

const MOCK_DISCOVERED: DiscoveredPrinter[] = [
  { printerBridgeId: 'bridge-thermal-1', name: 'Zebra ZQ320', type: 'THERMAL', status: 'ONLINE' },
  { printerBridgeId: 'bridge-a4-1', name: 'Bureau A4', type: 'A4', status: 'OFFLINE' },
];

describe('PrinterFormComponent', () => {
  let fixture: ComponentFixture<PrinterFormComponent>;
  let component: PrinterFormComponent;

  const printerRegistryServiceMock = {
    create: vi.fn().mockReturnValue(of(undefined)),
    ignore: vi.fn().mockReturnValue(of(undefined)),
  };
  const dialogRefMock = { close: vi.fn() };

  async function createComponent(data: PrinterFormDialogData): Promise<void> {
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [PrinterFormComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: PrinterRegistryService, useValue: printerRegistryServiceMock },
        { provide: DialogRef, useValue: dialogRefMock },
        { provide: DIALOG_DATA, useValue: data },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PrinterFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    vi.clearAllMocks();
    printerRegistryServiceMock.create.mockReturnValue(of(undefined));
    printerRegistryServiceMock.ignore.mockReturnValue(of(undefined));
    await createComponent({ discoveredPrinters: MOCK_DISCOVERED, discoveryError: false });
  });

  it('exposes the discovered printers received from the dialog data', () => {
    expect(component.discoveredPrinters()).toEqual(MOCK_DISCOVERED);
    expect(component.discoveryError()).toBe(false);
  });

  it('shows the list by default, with no printer selected', () => {
    expect(component.selectedPrinter()).toBeNull();
  });

  it('selectRow() selects the printer and derives its type', () => {
    component.selectRow(MOCK_DISCOVERED[0]);
    expect(component.selectedPrinter()).toEqual(MOCK_DISCOVERED[0]);
    expect(component.form.controls.printerBridgeId.value).toBe('bridge-thermal-1');
    expect(component.selectedType()).toBe('THERMAL');
  });

  it('backToList() clears the selection and resets the form without closing the dialog', () => {
    component.selectRow(MOCK_DISCOVERED[0]);
    component.form.controls.name.setValue('Guichet');

    component.backToList();

    expect(component.selectedPrinter()).toBeNull();
    expect(component.selectedType()).toBeNull();
    expect(component.form.controls.name.value).toBe('');
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('ignoreRow() removes the printer from the list and shows a success toast, without closing the dialog', async () => {
    await component.ignoreRow(MOCK_DISCOVERED[0]);

    expect(printerRegistryServiceMock.ignore).toHaveBeenCalledWith('bridge-thermal-1', 'Zebra ZQ320');
    expect(component.discoveredPrinters()).toEqual([MOCK_DISCOVERED[1]]);
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('ignoreRow() leaves the list untouched when the call fails', async () => {
    printerRegistryServiceMock.ignore.mockReturnValueOnce(throwError(() => new Error('server')));

    await component.ignoreRow(MOCK_DISCOVERED[0]);

    expect(component.discoveredPrinters()).toEqual(MOCK_DISCOVERED);
  });

  it('shows the discovery-unavailable state when the dialog data reports a discovery error', async () => {
    await createComponent({ discoveredPrinters: [], discoveryError: true });
    expect(component.discoveryError()).toBe(true);
  });

  it('form is invalid until a printer is selected', () => {
    component.form.controls.name.setValue('Guichet');
    expect(component.form.invalid).toBe(true);
  });

  it('selecting a THERMAL printer derives the type and requires widthMm', () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.printerBridgeId.setValue('bridge-thermal-1');
    expect(component.selectedType()).toBe('THERMAL');
    expect(component.form.controls.widthMm.hasError('required')).toBe(true);

    component.form.controls.widthMm.setValue(80);
    expect(component.form.valid).toBe(true);
  });

  it('selecting an A4 printer derives the type and does not require widthMm', () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.printerBridgeId.setValue('bridge-a4-1');
    expect(component.selectedType()).toBe('A4');
    expect(component.form.valid).toBe(true);
  });

  it('switching from a THERMAL to an A4 selection resets the stale widthMm value', () => {
    component.form.controls.printerBridgeId.setValue('bridge-thermal-1');
    component.form.controls.widthMm.setValue(80);
    component.form.controls.printerBridgeId.setValue('bridge-a4-1');
    expect(component.form.controls.widthMm.value).toBeNull();
  });

  it('does not call create when the form is invalid', async () => {
    await component.onSubmit();
    expect(printerRegistryServiceMock.create).not.toHaveBeenCalled();
  });

  it('calls create with the derived THERMAL payload on valid submit and closes the dialog', async () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.printerBridgeId.setValue('bridge-thermal-1');
    component.form.controls.widthMm.setValue(80);

    await component.onSubmit();

    expect(printerRegistryServiceMock.create).toHaveBeenCalledWith({
      name: 'Guichet',
      type: 'THERMAL',
      printerBridgeId: 'bridge-thermal-1',
      widthMm: 80,
    });
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
    expect(component.loading()).toBe(false);
  });

  it('calls create with the derived A4 payload on valid submit', async () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.printerBridgeId.setValue('bridge-a4-1');

    await component.onSubmit();

    expect(printerRegistryServiceMock.create).toHaveBeenCalledWith({
      name: 'Guichet',
      type: 'A4',
      printerBridgeId: 'bridge-a4-1',
      widthMm: null,
    });
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
  });

  it('sets error key and stops loading when create fails', async () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.printerBridgeId.setValue('bridge-thermal-1');
    component.form.controls.widthMm.setValue(80);
    printerRegistryServiceMock.create.mockReturnValue(throwError(() => new Error('server')));

    await component.onSubmit();

    expect(component.error()).toBe('admin.printers.error.create');
    expect(component.loading()).toBe(false);
  });

  it('cancel() closes the dialog', () => {
    component.cancel();
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
  });
});
