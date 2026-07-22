import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DialogRef } from '@angular/cdk/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PrinterFormComponent } from './printer-form.component';
import { PrinterRegistryService } from '../../../services/printer-registry.service';
import { SerialPortOption } from '../../../models/printer-registry.model';

const MOCK_SERIAL_PORTS: SerialPortOption[] = [
  { systemPortName: 'COM3', descriptiveName: 'Zebra ZQ320 Bluetooth' },
];

describe('PrinterFormComponent', () => {
  let fixture: ComponentFixture<PrinterFormComponent>;
  let component: PrinterFormComponent;

  const printerRegistryServiceMock = {
    listSerialPorts: vi.fn().mockReturnValue(of(MOCK_SERIAL_PORTS)),
    create: vi.fn().mockReturnValue(of(undefined)),
  };
  const dialogRefMock = { close: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    printerRegistryServiceMock.listSerialPorts.mockReturnValue(of(MOCK_SERIAL_PORTS));
    printerRegistryServiceMock.create.mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [PrinterFormComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: PrinterRegistryService, useValue: printerRegistryServiceMock },
        { provide: DialogRef, useValue: dialogRefMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PrinterFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('defaults type to THERMAL', () => {
    expect(component.form.controls.type.value).toBe('THERMAL');
  });

  it('loads serial ports on init', () => {
    expect(printerRegistryServiceMock.listSerialPorts).toHaveBeenCalledTimes(1);
    expect(component.serialPorts()).toEqual(MOCK_SERIAL_PORTS);
  });

  it('form is invalid when THERMAL fields are empty', () => {
    component.form.controls.name.setValue('Guichet');
    expect(component.form.invalid).toBe(true);
  });

  it('form is valid once THERMAL required fields are filled', () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.serialPort.setValue('COM3');
    component.form.controls.widthMm.setValue(80);
    expect(component.form.valid).toBe(true);
  });

  it('switching type to A4 clears THERMAL validators and requires host', () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.type.setValue('A4');
    expect(component.form.controls.serialPort.hasError('required')).toBe(false);
    expect(component.form.controls.widthMm.hasError('required')).toBe(false);
    expect(component.form.invalid).toBe(true);

    component.form.controls.host.setValue('192.168.1.50');
    expect(component.form.valid).toBe(true);
  });

  it('switching back to THERMAL clears the A4 host validator', () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.type.setValue('A4');
    component.form.controls.type.setValue('THERMAL');
    expect(component.form.controls.host.hasError('required')).toBe(false);
  });

  it('switching from THERMAL to A4 resets the stale serialPort/widthMm values', () => {
    component.form.controls.serialPort.setValue('COM3');
    component.form.controls.widthMm.setValue(80);
    component.form.controls.type.setValue('A4');
    expect(component.form.controls.serialPort.value).toBeNull();
    expect(component.form.controls.widthMm.value).toBeNull();
  });

  it('switching from A4 to THERMAL resets the stale host and port values', () => {
    component.form.controls.type.setValue('A4');
    component.form.controls.host.setValue('192.168.1.50');
    component.form.controls.port.setValue(9100);
    component.form.controls.type.setValue('THERMAL');
    expect(component.form.controls.host.value).toBeNull();
    expect(component.form.controls.port.value).toBeNull();
  });

  it('rejects a port below the valid TCP range', () => {
    component.form.controls.port.setValue(0);
    expect(component.form.controls.port.hasError('min')).toBe(true);
  });

  it('rejects a port above the valid TCP range', () => {
    component.form.controls.port.setValue(65536);
    expect(component.form.controls.port.hasError('max')).toBe(true);
  });

  it('accepts a port within the valid TCP range', () => {
    component.form.controls.port.setValue(9100);
    expect(component.form.controls.port.valid).toBe(true);
  });

  it('does not call create when the form is invalid', async () => {
    await component.onSubmit();
    expect(printerRegistryServiceMock.create).not.toHaveBeenCalled();
  });

  it('calls create with THERMAL payload on valid submit and closes the dialog', async () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.serialPort.setValue('COM3');
    component.form.controls.widthMm.setValue(80);

    await component.onSubmit();

    expect(printerRegistryServiceMock.create).toHaveBeenCalledWith({
      name: 'Guichet',
      type: 'THERMAL',
      serialPort: 'COM3',
      widthMm: 80,
      host: null,
      port: null,
    });
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
    expect(component.loading()).toBe(false);
  });

  it('calls create with A4 payload on valid submit', async () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.type.setValue('A4');
    component.form.controls.host.setValue('192.168.1.50');

    await component.onSubmit();

    expect(printerRegistryServiceMock.create).toHaveBeenCalledWith({
      name: 'Guichet',
      type: 'A4',
      serialPort: null,
      widthMm: null,
      host: '192.168.1.50',
      port: null,
    });
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
  });

  it('shows a notification when no serial port is detected', () => {
    printerRegistryServiceMock.listSerialPorts.mockReturnValue(of([]));
    fixture = TestBed.createComponent(PrinterFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    expect(component.serialPorts()).toEqual([]);
  });

  it('sets error key and stops loading when create fails', async () => {
    component.form.controls.name.setValue('Guichet');
    component.form.controls.serialPort.setValue('COM3');
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
