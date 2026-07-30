import { TestBed, ComponentFixture } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { of, Subject, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PosPageComponent } from './pos-page.component';
import { PosService } from '../../../services/pos.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ScanResult } from '../../../models/pos.model';

const MOCK_ITEM: ScanResult = { itemId: 1, name: 'Kapla', price: 5, incomplete: false, comment: null };
const MOCK_INCOMPLETE_ITEM: ScanResult = { itemId: 2, name: 'Puzzle', price: 3, incomplete: true, comment: 'Manque une piece' };

describe('PosPageComponent', () => {
  let fixture: ComponentFixture<PosPageComponent>;
  let component: PosPageComponent;

  const posServiceMock = { scan: vi.fn() };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();

    await TestBed.configureTestingModule({
      imports: [PosPageComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: PosService, useValue: posServiceMock },
        { provide: ToastService, useValue: toastMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PosPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('adds the item to the basket on a successful scan and clears any prior issue (AC4)', async () => {
    posServiceMock.scan.mockReturnValue(of(MOCK_ITEM));
    await component.onScan('00010001');
    expect(component.basket()).toEqual([MOCK_ITEM]);
    expect(component.lastScanIssue()).toBeNull();
  });

  it('adds the item AND shows a warning when it is incomplete (AC6)', async () => {
    posServiceMock.scan.mockReturnValue(of(MOCK_INCOMPLETE_ITEM));
    await component.onScan('00010002');
    expect(component.basket()).toEqual([MOCK_INCOMPLETE_ITEM]);
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.warning.incomplete', variant: 'warning' });
  });

  it('does not duplicate an item already in the basket, and warns instead (Review finding)', async () => {
    posServiceMock.scan.mockReturnValue(of(MOCK_ITEM));
    await component.onScan('00010001');
    await component.onScan('00010001');
    expect(component.basket()).toEqual([MOCK_ITEM]);
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.warning.alreadyInBasket', variant: 'warning' });
  });

  it('serializes concurrent scans so an overlapping call cannot bypass the duplicate guard (Review finding, round 2)', async () => {
    const subject = new Subject<ScanResult>();
    posServiceMock.scan.mockReturnValue(subject.asObservable());

    const firstCall = component.onScan('00010001');
    const secondCall = component.onScan('00010001'); // fired before the first resolves

    subject.next(MOCK_ITEM);
    subject.complete();
    await Promise.all([firstCall, secondCall]);

    expect(posServiceMock.scan).toHaveBeenCalledTimes(1);
    expect(component.basket()).toEqual([MOCK_ITEM]);
  });

  it('shows an inline error and does not add the item when already sold (AC5)', async () => {
    posServiceMock.scan.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { type: 'https://pluribourse/errors/item-already-sold' } }))
    );
    await component.onScan('00010001');
    expect(component.basket()).toEqual([]);
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.error.alreadySold', variant: 'error' });
  });

  it('shows a generic inline error and does not add the item when not found (AC7)', async () => {
    posServiceMock.scan.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { type: 'https://pluribourse/errors/item-not-found' } }))
    );
    await component.onScan('99999999');
    expect(component.basket()).toEqual([]);
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.pos.error.notFound', variant: 'error' });
  });

  it('reuses the existing no-active-edition contract', async () => {
    posServiceMock.scan.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { type: 'https://pluribourse/errors/no-active-edition' } }))
    );
    await component.onScan('00010001');
    expect(component.lastScanIssue()).toEqual({ message: 'volunteer.deposit.error.noActiveEdition', variant: 'error' });
  });

  it('shows a generic toast on any other error', async () => {
    posServiceMock.scan.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422, error: { type: 'https://pluribourse/errors/sale-phase-required' } })));
    await component.onScan('00010001');
    expect(component.lastScanIssue()).toBeNull();
    expect(toastMock.showError).toHaveBeenCalledWith('volunteer.pos.error.generic');
  });
});
