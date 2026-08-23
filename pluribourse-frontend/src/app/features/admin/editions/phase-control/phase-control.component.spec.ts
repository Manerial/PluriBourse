import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PhaseControlComponent } from './phase-control.component';
import { EditionService } from '../../../../services/edition.service';
import { SettlementService } from '../../../../services/settlement.service';
import { ReportService } from '../../../../services/report.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionDto } from '../../../../models/edition.model';
import { Language } from '../../../../models/language.enum';
import { SettlementDto } from '../../../../models/settlement.model';

const MOCK_EDITION: EditionDto = {
  id: 1, name: 'Bourse 2026', phase: 'PREPARATION',
  commissionRate: 20, documentLanguage: Language.EN, createdAt: '2026-01-01', archived: false,
  startDate: '2026-06-01', endDate: '2026-06-03'
};

const UNSETTLED_SETTLEMENT: SettlementDto = {
  sellerId: 1, firstName: 'Alice', lastName: 'Vendeuse', phone: '0600000001', email: 'alice@email.com',
  amountDue: 18, status: 'UNSETTLED',
};

const SETTLED_SETTLEMENT: SettlementDto = {
  sellerId: 2, firstName: 'Bob', lastName: 'Vendeur', phone: '0600000002', email: 'bob@email.com',
  amountDue: 9, status: 'SETTLED',
};

describe('PhaseControlComponent', () => {
  let fixture: ComponentFixture<PhaseControlComponent>;
  let component: PhaseControlComponent;

  const editionServiceMock = {
    getById: vi.fn().mockReturnValue(of(MOCK_EDITION)),
    advancePhase: vi.fn().mockReturnValue(of({ ...MOCK_EDITION, phase: 'DEPOSIT' })),
    rollbackPhase: vi.fn().mockReturnValue(of(MOCK_EDITION)),
    closeEdition: vi.fn().mockReturnValue(of({ ...MOCK_EDITION, phase: 'CLOSED' })),
    archiveEdition: vi.fn().mockReturnValue(of({ ...MOCK_EDITION, phase: 'CLOSED', archived: true })),
  };
  const settlementServiceMock = {
    getSettlements: vi.fn().mockReturnValue(of([SETTLED_SETTLEMENT])),
  };
  const reportServiceMock = {
    printEditionReportClosure: vi.fn().mockReturnValue(of(undefined)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const confirmMock = { open: vi.fn().mockReturnValue(of(false)) };
  const dialogRefMock = { close: vi.fn() };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION));
    editionServiceMock.closeEdition.mockReturnValue(of({ ...MOCK_EDITION, phase: 'CLOSED' }));
    editionServiceMock.archiveEdition.mockReturnValue(of({ ...MOCK_EDITION, phase: 'CLOSED', archived: true }));
    settlementServiceMock.getSettlements.mockReturnValue(of([SETTLED_SETTLEMENT]));
    reportServiceMock.printEditionReportClosure.mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [PhaseControlComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: DIALOG_DATA, useValue: { editionId: 1 } },
        { provide: DialogRef, useValue: dialogRefMock },
        { provide: EditionService, useValue: editionServiceMock },
        { provide: SettlementService, useValue: settlementServiceMock },
        { provide: ReportService, useValue: reportServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ConfirmDialogService, useValue: confirmMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PhaseControlComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads edition on init', () => {
    expect(editionServiceMock.getById).toHaveBeenCalledWith(1);
    expect(component.edition()?.phase).toBe('PREPARATION');
  });

  it('sets error key when load fails', async () => {
    editionServiceMock.getById.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('phase.control.error.load');
  });

  it('canAdvance returns true when phase is PREPARATION', () => {
    expect(component.canAdvance()).toBe(true);
  });

  it('canRollback returns false when phase is PREPARATION', () => {
    expect(component.canRollback()).toBe(false);
  });

  it('canRollback returns false when CLOSED and archived', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: true });
    expect(component.canRollback()).toBe(false);
  });

  it('canRollback returns true when CLOSED and not archived', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: false });
    expect(component.canRollback()).toBe(true);
  });

  it('nextPhase returns DEPOSIT when current is PREPARATION', () => {
    expect(component.nextPhase()).toBe('DEPOSIT');
  });

  it('prevPhase returns null when current is PREPARATION', () => {
    expect(component.prevPhase()).toBeNull();
  });

  it('confirmAdvance opens confirm dialog', () => {
    component.confirmAdvance();
    expect(confirmMock.open).toHaveBeenCalledOnce();
  });

  it('confirmAdvance — confirmed: calls advancePhase and shows success toast', async () => {
    confirmMock.open.mockReturnValue(of(true));
    component.confirmAdvance();
    await fixture.whenStable();
    expect(editionServiceMock.advancePhase).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.edition()?.phase).toBe('DEPOSIT');
  });

  it('confirmAdvance — confirmed: shows error toast when advancePhase fails', async () => {
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.advancePhase.mockReturnValue(throwError(() => new Error('server error')));
    component.confirmAdvance();
    await fixture.whenStable();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('confirmAdvance — confirmed: shows specific error toast when no categories are configured', async () => {
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.advancePhase.mockReturnValue(throwError(() => new HttpErrorResponse({
      status: 422,
      error: { type: 'https://pluribourse/errors/no-categories-configured' },
    })));
    component.confirmAdvance();
    await fixture.whenStable();
    expect(toastMock.showError).toHaveBeenCalledWith('phase.advance.error.noCategoriesConfigured');
  });

  it('confirmAdvance — confirmed: closes the dialog after a successful advance', async () => {
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.advancePhase.mockReturnValue(of({ ...MOCK_EDITION, phase: 'DEPOSIT' }));
    component.confirmAdvance();
    await fixture.whenStable();
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
  });

  it('confirmAdvance — confirmed: does not close the dialog when advancePhase fails', async () => {
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.advancePhase.mockReturnValue(throwError(() => new Error('server error')));
    component.confirmAdvance();
    await fixture.whenStable();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('confirmAdvance shows the no-items warning when advancing from DEPOSIT with hasItems false', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'DEPOSIT', hasItems: false });
    component.confirmAdvance();
    expect(confirmMock.open).toHaveBeenCalledWith(expect.objectContaining({ description: 'phase.advance.dialog.warningNoItems' }));
  });

  it('confirmAdvance shows the normal description when advancing from DEPOSIT with hasItems true', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'DEPOSIT', hasItems: true });
    component.confirmAdvance();
    expect(confirmMock.open).toHaveBeenCalledWith(expect.objectContaining({ description: 'phase.advance.dialog.description.DEPOSIT' }));
  });

  it('canAdvance returns false when phase is POST_SALE (dedicated close flow takes over)', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'POST_SALE' });
    expect(component.canAdvance()).toBe(false);
  });

  it('canAdvance returns false when edition is null', () => {
    component['edition'].set(null);
    expect(component.canAdvance()).toBe(false);
  });

  it('confirmRollback opens confirm dialog when phase allows rollback', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'DEPOSIT' });
    component.confirmRollback();
    expect(confirmMock.open).toHaveBeenCalledOnce();
  });

  it('confirmRollback — confirmed: calls rollbackPhase and shows success toast', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'DEPOSIT' });
    confirmMock.open.mockReturnValue(of(true));
    component.confirmRollback();
    await fixture.whenStable();
    expect(editionServiceMock.rollbackPhase).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('confirmRollback — confirmed: shows error toast when rollbackPhase fails', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'DEPOSIT' });
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.rollbackPhase.mockReturnValue(throwError(() => new Error('server error')));
    component.confirmRollback();
    await fixture.whenStable();
    expect(toastMock.showError).toHaveBeenCalledOnce();
  });

  it('confirmRollback — confirmed: closes the dialog after a successful rollback', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'DEPOSIT' });
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.rollbackPhase.mockReturnValue(of(MOCK_EDITION));
    component.confirmRollback();
    await fixture.whenStable();
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
  });

  it('confirmRollback — confirmed: does not close the dialog when rollbackPhase fails', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'DEPOSIT' });
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.rollbackPhase.mockReturnValue(throwError(() => new Error('server error')));
    component.confirmRollback();
    await fixture.whenStable();
    expect(dialogRefMock.close).not.toHaveBeenCalled();
  });

  it('canClose returns true only when phase is POST_SALE', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'POST_SALE' });
    expect(component.canClose()).toBe(true);
    component['edition'].set({ ...MOCK_EDITION, phase: 'SALE' });
    expect(component.canClose()).toBe(false);
  });

  it('canArchive returns true whenever CLOSED and not archived, regardless of hasItems', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: false, hasItems: true });
    expect(component.canArchive()).toBe(true);
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: false, hasItems: false });
    expect(component.canArchive()).toBe(true);
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: true, hasItems: true });
    expect(component.canArchive()).toBe(false);
    component['edition'].set({ ...MOCK_EDITION, phase: 'POST_SALE', archived: false, hasItems: true });
    expect(component.canArchive()).toBe(false);
  });

  it('confirmClose shows the standard description when no seller is unsettled', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'POST_SALE' });
    settlementServiceMock.getSettlements.mockReturnValue(of([SETTLED_SETTLEMENT]));
    await component.confirmClose();
    expect(confirmMock.open).toHaveBeenCalledWith(expect.objectContaining({ description: 'phase.close.dialog.description' }));
  });

  it('confirmClose builds the unsettled warning with the correct interpolated count and summed amount due', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'POST_SALE' });
    settlementServiceMock.getSettlements.mockReturnValue(of([UNSETTLED_SETTLEMENT, SETTLED_SETTLEMENT]));
    const instantSpy = vi.spyOn(component['translate'], 'instant');
    await component.confirmClose();
    expect(instantSpy).toHaveBeenCalledWith('phase.close.dialog.warningUnsettled', { count: 1, amount: '18.00' });
  });

  it('confirmClose — confirmed: closes the edition, closes the dialog, and triggers the closure print', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'POST_SALE' });
    confirmMock.open.mockReturnValue(of(true));
    await component.confirmClose();
    await fixture.whenStable();
    expect(editionServiceMock.closeEdition).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
    expect(reportServiceMock.printEditionReportClosure).toHaveBeenCalledWith(1);
  });

  it('confirmClose — confirmed: a print-closure failure still leaves the close as a success', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'POST_SALE' });
    confirmMock.open.mockReturnValue(of(true));
    reportServiceMock.printEditionReportClosure.mockReturnValue(throwError(() => new Error('print failed')));
    await component.confirmClose();
    await fixture.whenStable();
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
    expect(toastMock.showError).toHaveBeenCalledWith('phase.close.error.printReport');
  });

  it('confirmArchive opens a confirm dialog with the error variant', () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: false, hasItems: true });
    component.confirmArchive();
    expect(confirmMock.open).toHaveBeenCalledWith(expect.objectContaining({ confirmVariant: 'error' }));
  });

  it('confirmArchive — confirmed: archives the edition and closes the dialog', async () => {
    component['edition'].set({ ...MOCK_EDITION, phase: 'CLOSED', archived: false, hasItems: true });
    confirmMock.open.mockReturnValue(of(true));
    component.confirmArchive();
    await fixture.whenStable();
    expect(editionServiceMock.archiveEdition).toHaveBeenCalledWith(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(dialogRefMock.close).toHaveBeenCalledOnce();
  });

  it('reopening the dialog after closing re-fetches the edition and reflects the refreshed hasItems', async () => {
    editionServiceMock.getById.mockReturnValue(of({ ...MOCK_EDITION, phase: 'CLOSED', hasItems: true }));
    await component.ngOnInit();
    expect(component.edition()?.hasItems).toBe(true);
  });
});
