import { AfterViewInit, Component, DestroyRef, ElementRef, HostListener, inject, output, signal, viewChild } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

// USB HID barcode scanners send digits without Shift, exactly like a US keyboard. On an AZERTY
// OS, KeyboardEvent.key would reflect the AZERTY symbol on that physical key (e.g. "&" instead
// of "1") — KeyboardEvent.code is the physical key identifier, independent of the OS layout, and
// is the only reliable way to decode the digit (FR-034, NFR-005).
const CODE_TO_DIGIT: Record<string, string> = {
  Digit0: '0', Digit1: '1', Digit2: '2', Digit3: '3', Digit4: '4',
  Digit5: '5', Digit6: '6', Digit7: '7', Digit8: '8', Digit9: '9',
  Numpad0: '0', Numpad1: '1', Numpad2: '2', Numpad3: '3', Numpad4: '4',
  Numpad5: '5', Numpad6: '6', Numpad7: '7', Numpad8: '8', Numpad9: '9',
};

const REFOCUS_DELAY_MS = 500;
// Deliberately much longer than REFOCUS_DELAY_MS: a human typing a barcode by hand can easily
// pause more than 500ms between digits (re-checking the label, hesitation) — clearing the buffer
// on the same short window as the refocus check would wipe a still-in-progress manual entry.
const BUFFER_STALE_DELAY_MS = 3000;
const BARCODE_LENGTH = 8;

// Native tags whose focus must never be stolen by the perpetual refocus timer.
const EDITABLE_TAGS = new Set(['INPUT', 'TEXTAREA', 'SELECT']);
// ARIA roles covering composite widgets (Angular Material's mat-select, CDK overlays/dialogs)
// that aren't native <input>/<select> elements but must be protected the same way — a plain
// <button> is deliberately NOT covered here: the scanner is meant to reclaim focus from
// incidental clicks on the page chrome, just not from another field or widget mid-interaction.
const PROTECTED_ROLES = new Set(['dialog', 'alertdialog', 'listbox', 'combobox', 'menu', 'menuitem', 'option']);

@Component({
  selector: 'app-scanner-input',
  standalone: true,
  imports: [MatFormFieldModule, MatInputModule, TranslatePipe],
  templateUrl: './scanner-input.component.html',
  styleUrl: './scanner-input.component.scss',
})
export class ScannerInputComponent implements AfterViewInit {
  private readonly destroyRef = inject(DestroyRef);

  readonly scannerInput = viewChild<ElementRef<HTMLInputElement>>('scannerInput');

  readonly barcodeScanned = output<string>();

  readonly buffer = signal('');

  private refocusTimer: ReturnType<typeof setTimeout> | undefined;
  private staleBufferTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.armRefocusTimer();
    this.destroyRef.onDestroy(() => {
      clearTimeout(this.refocusTimer);
      clearTimeout(this.staleBufferTimer);
    });
  }

  ngAfterViewInit(): void {
    // Deferred: focusing synchronously here mutates MatFormField's placeholder/label host
    // bindings mid change-detection cycle, tripping ExpressionChangedAfterItHasBeenCheckedError
    // (same pitfall already hit in SellerSearchComponent).
    queueMicrotask(() => this.scannerInput()?.nativeElement.focus());
  }

  // Any keystroke anywhere on the page re-arms the timer — the volunteer may have clicked
  // elsewhere, not necessarily typed in this field.
  @HostListener('document:keydown')
  onAnyKeydown(): void {
    this.armRefocusTimer();
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.code === 'Tab') {
      // Let Tab/Shift+Tab move focus normally — trapping it would violate the project's
      // WCAG 2.2 AA tab-order requirement.
      return;
    }
    const digit = CODE_TO_DIGIT[event.code];
    if (digit !== undefined) {
      event.preventDefault();
      if (this.buffer().length < BARCODE_LENGTH) {
        this.buffer.update(value => value + digit);
      }
      this.armStaleBufferTimer();
      return;
    }
    if (event.code === 'Enter' || event.code === 'NumpadEnter') {
      event.preventDefault();
      this.submit();
      return;
    }
    if (event.code === 'Backspace') {
      event.preventDefault();
      this.buffer.update(value => value.slice(0, -1));
      this.armStaleBufferTimer();
      return;
    }
    // Everything else — including modifier combos such as Ctrl+Backspace (native word-delete) —
    // is blocked: letting them through would let the browser edit the native DOM value directly,
    // desyncing it from this signal-driven buffer. Manual paste is handled separately below,
    // through a dedicated clipboard event rather than by letting Ctrl+V reach the DOM.
    event.preventDefault();
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const digitsOnly = (event.clipboardData?.getData('text') ?? '').replace(/\D/g, '').slice(0, BARCODE_LENGTH);
    if (digitsOnly.length > 0) {
      this.buffer.set(digitsOnly);
      this.armStaleBufferTimer();
    }
  }

  private submit(): void {
    const barcode = this.buffer();
    this.buffer.set('');
    clearTimeout(this.staleBufferTimer);
    if (barcode.length > 0) {
      this.barcodeScanned.emit(barcode);
    }
  }

  private armRefocusTimer(): void {
    clearTimeout(this.refocusTimer);
    this.refocusTimer = setTimeout(() => this.refocus(), REFOCUS_DELAY_MS);
  }

  // Perpetual, not one-shot: reschedules itself every time it fires so the 500ms idle check
  // keeps running for the component's entire lifetime, not just once after the last keystroke.
  private refocus(): void {
    const activeElement = document.activeElement;
    const inputEl = this.scannerInput()?.nativeElement;
    if (!this.isProtectedElement(activeElement, inputEl)) {
      inputEl?.focus();
    }
    this.armRefocusTimer();
  }

  private isProtectedElement(activeElement: Element | null, inputEl: HTMLInputElement | null | undefined): boolean {
    if (activeElement === null || activeElement === inputEl) {
      return false;
    }
    if (EDITABLE_TAGS.has(activeElement.tagName)) {
      return true;
    }
    const role = activeElement.getAttribute('role');
    return role !== null && PROTECTED_ROLES.has(role);
  }

  // A buffer that survived this longer idle window without an Enter is an abandoned partial
  // scan — clearing it stops it from silently merging into the digits of the next real scan.
  private armStaleBufferTimer(): void {
    clearTimeout(this.staleBufferTimer);
    this.staleBufferTimer = setTimeout(() => this.buffer.set(''), BUFFER_STALE_DELAY_MS);
  }
}
