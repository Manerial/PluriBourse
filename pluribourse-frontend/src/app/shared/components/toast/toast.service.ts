import { Injectable, signal } from '@angular/core';

export interface ToastLink {
  path: string;
  label: string;
}

export interface Toast {
  message: string;
  type: 'success' | 'error';
  link?: ToastLink;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private readonly _toast = signal<Toast | null>(null);
  readonly toast = this._toast.asReadonly();
  private _timer: ReturnType<typeof setTimeout> | null = null;

  showSuccess(message: string): void {
    this._show({ message, type: 'success' }, 4000);
  }

  showError(message: string, link?: ToastLink): void {
    this._show({ message, type: 'error', link });
  }

  close(): void {
    this._clearTimer();
    this._toast.set(null);
  }

  private _show(toast: Toast, autoDismissMs?: number): void {
    this._clearTimer();
    this._toast.set(toast);
    if (autoDismissMs !== undefined) {
      this._timer = setTimeout(() => {
        this._toast.set(null);
        this._timer = null;
      }, autoDismissMs);
    }
  }

  private _clearTimer(): void {
    if (this._timer !== null) {
      clearTimeout(this._timer);
      this._timer = null;
    }
  }
}
