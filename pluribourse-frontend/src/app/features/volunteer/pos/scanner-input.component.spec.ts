import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { vi } from 'vitest';
import { ScannerInputComponent } from './scanner-input.component';

function keydown(code: string, key = code, extra: Partial<KeyboardEventInit> = {}): KeyboardEvent {
  return new KeyboardEvent('keydown', { code, key, cancelable: true, ...extra });
}

function paste(text: string): ClipboardEvent {
  // jsdom implements neither DataTransfer nor the ClipboardEvent constructor — a plain Event
  // with a clipboardData property exposing getData() is all onPaste() actually reads.
  const event = new Event('paste', { cancelable: true }) as ClipboardEvent;
  Object.defineProperty(event, 'clipboardData', { value: { getData: () => text } });
  return event;
}

describe('ScannerInputComponent', () => {
  let fixture: ComponentFixture<ScannerInputComponent>;
  let component: ScannerInputComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ScannerInputComponent],
      providers: [provideTranslateService({ lang: 'en' })],
    }).compileComponents();

    fixture = TestBed.createComponent(ScannerInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  it('focuses the scanner input on init (AC1)', () => {
    expect(document.activeElement).toBe(component.scannerInput()?.nativeElement);
  });

  it('decodes digits from event.code regardless of event.key (AC3, AZERTY/QWERTY)', () => {
    const input = component.scannerInput()!.nativeElement;
    // On an AZERTY OS, the physical "1"/"2" keys (Digit1/Digit2) produce "&"/"é" via event.key —
    // only event.code must drive the decoding.
    input.dispatchEvent(keydown('Digit1', '&'));
    input.dispatchEvent(keydown('Digit2', 'é'));
    input.dispatchEvent(keydown('Numpad3', '3'));
    expect(component.buffer()).toBe('123');
  });

  it('ignores non-digit keys and supports Backspace for manual correction', () => {
    const input = component.scannerInput()!.nativeElement;
    input.dispatchEvent(keydown('Digit1'));
    input.dispatchEvent(keydown('KeyA'));
    input.dispatchEvent(keydown('Digit2'));
    input.dispatchEvent(keydown('Backspace'));
    expect(component.buffer()).toBe('1');
  });

  it('caps the buffer at 8 digits — extra digits are ignored (Review finding)', () => {
    const input = component.scannerInput()!.nativeElement;
    for (const code of ['Digit1', 'Digit2', 'Digit3', 'Digit4', 'Digit5', 'Digit6', 'Digit7', 'Digit8', 'Digit9']) {
      input.dispatchEvent(keydown(code));
    }
    expect(component.buffer()).toBe('12345678');
  });

  it('does not prevent Tab/Shift+Tab so keyboard-only users can leave the field (Review finding)', () => {
    const input = component.scannerInput()!.nativeElement;
    const notCanceled = input.dispatchEvent(keydown('Tab'));
    const notCanceledShiftTab = input.dispatchEvent(keydown('Tab', 'Tab', { shiftKey: true }));
    expect(notCanceled).toBe(true);
    expect(notCanceledShiftTab).toBe(true);
  });

  it('blocks modifier combos such as Ctrl+Backspace so the native DOM value cannot desync from the buffer (Review finding, round 2)', () => {
    const input = component.scannerInput()!.nativeElement;
    const canceled = !input.dispatchEvent(keydown('Backspace', 'Backspace', { ctrlKey: true }));
    expect(canceled).toBe(true);
  });

  it('supports manual paste of a barcode via a dedicated clipboard handler (Review finding, round 2)', () => {
    const input = component.scannerInput()!.nativeElement;
    input.dispatchEvent(paste('00010001'));
    expect(component.buffer()).toBe('00010001');
  });

  it('strips non-digits and caps pasted content at 8 digits (Review finding, round 2)', () => {
    const input = component.scannerInput()!.nativeElement;
    input.dispatchEvent(paste('0001-0001-extra'));
    expect(component.buffer()).toBe('00010001');
  });

  it('emits barcodeScanned on Enter and clears the buffer', () => {
    const scannedSpy = vi.fn();
    component.barcodeScanned.subscribe(scannedSpy);
    const input = component.scannerInput()!.nativeElement;

    for (const code of ['Digit0', 'Digit0', 'Digit0', 'Digit1', 'Digit0', 'Digit0', 'Digit0', 'Digit1']) {
      input.dispatchEvent(keydown(code));
    }
    input.dispatchEvent(keydown('Enter'));

    expect(scannedSpy).toHaveBeenCalledWith('00010001');
    expect(component.buffer()).toBe('');
  });

  it('emits on NumpadEnter as well', () => {
    const scannedSpy = vi.fn();
    component.barcodeScanned.subscribe(scannedSpy);
    const input = component.scannerInput()!.nativeElement;
    input.dispatchEvent(keydown('Digit1'));
    input.dispatchEvent(keydown('NumpadEnter'));
    expect(scannedSpy).toHaveBeenCalledWith('1');
  });

  it('does not emit on Enter when the buffer is empty', () => {
    const scannedSpy = vi.fn();
    component.barcodeScanned.subscribe(scannedSpy);
    component.scannerInput()!.nativeElement.dispatchEvent(keydown('Enter'));
    expect(scannedSpy).not.toHaveBeenCalled();
  });

  it('does not clear a partial buffer within the 500ms refocus window, so slow manual typing survives (Review finding, round 2)', () => {
    vi.useFakeTimers();
    fixture.destroy();
    const otherFixture = TestBed.createComponent(ScannerInputComponent);
    const otherComponent = otherFixture.componentInstance;
    otherFixture.detectChanges();

    const input = otherComponent.scannerInput()!.nativeElement;
    input.dispatchEvent(keydown('Digit9'));
    vi.advanceTimersByTime(500);

    expect(otherComponent.buffer()).toBe('9');
    otherFixture.destroy();
  });

  it('clears a stale partial buffer once the longer idle window elapses, so it cannot merge into the next scan (Review finding)', () => {
    vi.useFakeTimers();
    fixture.destroy();
    const otherFixture = TestBed.createComponent(ScannerInputComponent);
    const otherComponent = otherFixture.componentInstance;
    otherFixture.detectChanges();

    const input = otherComponent.scannerInput()!.nativeElement;
    input.dispatchEvent(keydown('Digit9'));
    expect(otherComponent.buffer()).toBe('9');

    vi.advanceTimersByTime(3000);

    expect(otherComponent.buffer()).toBe('');
    otherFixture.destroy();
  });

  it('re-focuses the input after 500ms of keyboard inactivity anywhere on the page (AC2)', () => {
    vi.useFakeTimers();
    fixture.destroy();

    const otherFixture = TestBed.createComponent(ScannerInputComponent);
    const otherComponent = otherFixture.componentInstance;
    otherFixture.detectChanges();

    const button = document.createElement('button');
    document.body.appendChild(button);
    button.focus();
    expect(document.activeElement).toBe(button);

    document.dispatchEvent(keydown('KeyA'));
    vi.advanceTimersByTime(500);

    expect(document.activeElement).toBe(otherComponent.scannerInput()?.nativeElement);

    document.body.removeChild(button);
    otherFixture.destroy();
  });

  it('keeps re-checking every 500ms — the timer is perpetual, not one-shot (Review finding)', () => {
    vi.useFakeTimers();
    fixture.destroy();

    const otherFixture = TestBed.createComponent(ScannerInputComponent);
    const otherComponent = otherFixture.componentInstance;
    otherFixture.detectChanges();

    const button = document.createElement('button');
    document.body.appendChild(button);

    // First cycle: focus leaves the field, no keystroke — the very first (initial) timer fires
    // and reclaims focus.
    button.focus();
    vi.advanceTimersByTime(500);
    expect(document.activeElement).toBe(otherComponent.scannerInput()?.nativeElement);

    // Second cycle, well after the first firing: focus leaves again, no keystroke in between —
    // a one-shot timer would never fire again here, but a perpetual one still reclaims it.
    button.focus();
    expect(document.activeElement).toBe(button);
    vi.advanceTimersByTime(500);
    expect(document.activeElement).toBe(otherComponent.scannerInput()?.nativeElement);

    document.body.removeChild(button);
    otherFixture.destroy();
  });

  it('does not steal focus from another currently-focused form field (Review finding)', () => {
    vi.useFakeTimers();
    fixture.destroy();
    const otherFixture = TestBed.createComponent(ScannerInputComponent);
    otherFixture.detectChanges();

    const otherInput = document.createElement('input');
    document.body.appendChild(otherInput);
    otherInput.focus();
    expect(document.activeElement).toBe(otherInput);

    vi.advanceTimersByTime(500);

    expect(document.activeElement).toBe(otherInput);
    document.body.removeChild(otherInput);
  });

  it('renders the native input as disabled when the disabled input is set (Story 4.6)', async () => {
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(component.scannerInput()!.nativeElement.disabled).toBe(true);
  });

  it('does not steal focus from a composite ARIA widget such as an open mat-select (Review finding, round 2)', () => {
    vi.useFakeTimers();
    fixture.destroy();
    const otherFixture = TestBed.createComponent(ScannerInputComponent);
    otherFixture.detectChanges();

    const listbox = document.createElement('div');
    listbox.setAttribute('role', 'listbox');
    listbox.tabIndex = -1;
    document.body.appendChild(listbox);
    listbox.focus();
    expect(document.activeElement).toBe(listbox);

    vi.advanceTimersByTime(500);

    expect(document.activeElement).toBe(listbox);
    document.body.removeChild(listbox);
  });
});
