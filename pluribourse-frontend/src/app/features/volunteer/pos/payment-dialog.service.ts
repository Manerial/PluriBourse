import { inject, Injectable } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { TranslateService } from '@ngx-translate/core';
import { Observable } from 'rxjs';
import { PaymentDialogResult } from '../../../models/pos.model';
import { PaymentDialogComponent, PaymentDialogData } from './payment-dialog.component';

@Injectable({ providedIn: 'root' })
export class PaymentDialogService {
  private readonly dialog = inject(Dialog);
  private readonly translate = inject(TranslateService);

  open(data: PaymentDialogData): Observable<PaymentDialogResult | undefined> {
    const ref = this.dialog.open<PaymentDialogResult | undefined, PaymentDialogData, PaymentDialogComponent>(
      PaymentDialogComponent,
      {
        data,
        hasBackdrop: true,
        backdropClass: 'dialog-backdrop',
        panelClass: 'dialog-panel',
        disableClose: false,
        ariaLabel: this.translate.instant('volunteer.pos.payment.title'),
        ariaDescribedBy: 'dialog-desc',
      }
    );
    return ref.closed;
  }
}
