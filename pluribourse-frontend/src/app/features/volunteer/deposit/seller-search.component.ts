import { AfterViewInit, Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';
import { catchError, of, switchMap } from 'rxjs';
import { SellerDto } from '../../../models/seller.model';
import { SellerService } from '../../../services/seller.service';
import { NotificationInlineComponent } from '../../../shared/components/notification-inline/notification-inline.component';
import { extractErrorType } from '../../../shared/http-error.util';
import { SellerFormComponent } from './seller-form.component';

@Component({
  selector: 'app-seller-search',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    TranslatePipe,
    NotificationInlineComponent,
    SellerFormComponent,
  ],
  templateUrl: './seller-search.component.html',
  styleUrl: './seller-search.component.scss'
})
export class SellerSearchComponent implements AfterViewInit {
  private readonly sellerService = inject(SellerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly searchInput = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly results = signal<SellerDto[]>([]);
  readonly searched = signal(false);
  readonly error = signal<string | null>(null);
  readonly selectedSeller = signal<SellerDto | null>(null);
  readonly showCreateForm = signal(false);

  constructor() {
    this.searchControl.valueChanges.pipe(
      switchMap(query => {
        const trimmed = query.trim();
        if (!trimmed) {
          this.searched.set(false);
          this.results.set([]);
          return of(null);
        }
        this.error.set(null);
        return this.sellerService.search(trimmed).pipe(
          catchError((err: unknown) => {
            if (err instanceof HttpErrorResponse && err.status === 404 && extractErrorType(err)?.endsWith('/no-active-edition')) {
              this.error.set('volunteer.deposit.error.noActiveEdition');
            } else {
              this.error.set('volunteer.deposit.error.search');
            }
            return of<SellerDto[]>([]);
          })
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => {
      if (result === null) {
        return;
      }
      this.results.set(result);
      this.searched.set(true);
    });
  }

  ngAfterViewInit(): void {
    // Deferred: focusing synchronously here mutates MatFormField's placeholder/label
    // host bindings mid change-detection cycle, tripping ExpressionChangedAfterItHasBeenCheckedError.
    queueMicrotask(() => this.searchInput()?.nativeElement.focus());
  }

  selectSeller(seller: SellerDto): void {
    this.selectedSeller.set(seller);
    this.showCreateForm.set(false);
    this.searchControl.setValue('', { emitEvent: false });
    this.results.set([]);
    this.searched.set(false);
  }

  changeSeller(): void {
    this.selectedSeller.set(null);
    queueMicrotask(() => this.searchInput()?.nativeElement.focus());
  }

  openCreateForm(): void {
    this.showCreateForm.set(true);
  }

  cancelCreateForm(): void {
    this.showCreateForm.set(false);
  }

  onSellerCreated(seller: SellerDto): void {
    this.showCreateForm.set(false);
    this.selectSeller(seller);
  }
}
