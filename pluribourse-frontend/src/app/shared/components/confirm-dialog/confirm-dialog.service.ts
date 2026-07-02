import { inject, Injectable } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { Observable } from 'rxjs';
import { ConfirmDialogComponent, ConfirmDialogData } from './confirm-dialog.component';

@Injectable({ providedIn: 'root' })
export class ConfirmDialogService {
  private readonly dialog = inject(Dialog);

  open(data: ConfirmDialogData): Observable<boolean | undefined> {
    const ref = this.dialog.open<boolean, ConfirmDialogData, ConfirmDialogComponent>(
      ConfirmDialogComponent,
      {
        data,
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabel: data.title,
        ariaDescribedBy: 'dialog-desc',
      }
    );
    return ref.closed;
  }
}
