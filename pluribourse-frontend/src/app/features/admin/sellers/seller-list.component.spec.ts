import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PageEvent } from '@angular/material/paginator';
import { SellerListComponent } from './seller-list.component';
import { SellerService } from '../../../services/seller.service';
import { PageResponse, SellerDto } from '../../../models/seller.model';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';

const MOCK_SELLERS: SellerDto[] = [
  { id: 1, firstName: 'Pierre', lastName: 'Martin', email: 'martin.pierre@email.com', phone: '0612345678' },
  { id: 2, firstName: 'Alice', lastName: 'Dupont', email: 'alice.dupont@email.com', phone: '0698765432' },
];

const MOCK_PAGE: PageResponse<SellerDto> = { content: MOCK_SELLERS, totalElements: 2, totalPages: 1, number: 0, size: 50 };

describe('SellerListComponent', () => {
  let fixture: ComponentFixture<SellerListComponent>;
  let component: SellerListComponent;

  const sellerServiceMock = {
    getSellers: vi.fn().mockReturnValue(of(MOCK_PAGE)),
    delete: vi.fn().mockReturnValue(of(undefined)),
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  const confirmDialogMock = {
    open: vi.fn().mockReturnValue(of(false)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    sellerServiceMock.getSellers.mockReturnValue(of(MOCK_PAGE));
    sellerServiceMock.delete.mockReturnValue(of(undefined));
    confirmDialogMock.open.mockReturnValue(of(false));

    await TestBed.configureTestingModule({
      imports: [SellerListComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: SellerService, useValue: sellerServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ConfirmDialogService, useValue: confirmDialogMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SellerListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads the first page of sellers on init (UX-DR11: size 50)', () => {
    expect(sellerServiceMock.getSellers).toHaveBeenCalledWith(0, 50);
    expect(component.sellers().length).toBe(2);
    expect(component.totalElements()).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('shows error key when load fails', async () => {
    sellerServiceMock.getSellers.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('admin.sellers.error.load');
  });

  it('shows a dedicated error key when load fails because no edition is active', async () => {
    sellerServiceMock.getSellers.mockReturnValue(
      throwError(() => new HttpErrorResponse({ error: { type: 'https://pluribourse/errors/no-active-edition' }, status: 404 }))
    );
    await component.ngOnInit();
    expect(component.error()).toBe('admin.sellers.error.noActiveEdition');
  });

  it('onPageChange() loads the requested page', async () => {
    const event = { pageIndex: 1, pageSize: 50, length: 2 } as PageEvent;
    await component.onPageChange(event);
    expect(sellerServiceMock.getSellers).toHaveBeenLastCalledWith(1, 50);
  });

  it('does not call delete when user cancels the confirm dialog', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(false));
    await component.confirmDelete(MOCK_SELLERS[0]);
    expect(sellerServiceMock.delete).not.toHaveBeenCalled();
  });

  it('calls delete and reloads the page after confirm', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(true));
    await component.confirmDelete(MOCK_SELLERS[0]);
    expect(sellerServiceMock.delete).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.submitting()).toBe(false);
  });

  it('shows error toast when delete fails', async () => {
    confirmDialogMock.open.mockReturnValueOnce(of(true));
    sellerServiceMock.delete.mockReturnValueOnce(throwError(() => new Error('server')));
    await component.confirmDelete(MOCK_SELLERS[0]);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });

  it('steps back a page when deleting the last row of a non-first page', async () => {
    const lastRowPage: PageResponse<SellerDto> = { content: [MOCK_SELLERS[0]], totalElements: 3, totalPages: 2, number: 1, size: 50 };
    sellerServiceMock.getSellers.mockReturnValueOnce(of(lastRowPage));
    await component.onPageChange({ pageIndex: 1, pageSize: 50, length: 3 } as PageEvent);
    expect(component.pageIndex()).toBe(1);

    confirmDialogMock.open.mockReturnValueOnce(of(true));
    await component.confirmDelete(MOCK_SELLERS[0]);

    expect(sellerServiceMock.getSellers).toHaveBeenLastCalledWith(0, 50);
  });
});
