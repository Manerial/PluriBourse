import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { PhaseControlComponent } from './phase-control.component';
import { EditionService } from '../../../../services/edition.service';
import { ToastService } from '../../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionDto } from '../../../../models/edition.model';
import { Language } from '../../../../models/language.enum';

const MOCK_EDITION: EditionDto = {
  id: 1, name: 'Bourse 2026', phase: 'PREPARATION',
  commissionRate: 20, documentLanguage: Language.EN, createdAt: '2026-01-01', archived: false,
  startDate: null, endDate: null
};

describe('PhaseControlComponent', () => {
  let fixture: ComponentFixture<PhaseControlComponent>;
  let component: PhaseControlComponent;

  const editionServiceMock = {
    getById: vi.fn().mockReturnValue(of(MOCK_EDITION)),
    advancePhase: vi.fn().mockReturnValue(of({ ...MOCK_EDITION, phase: 'DEPOSIT' })),
    rollbackPhase: vi.fn().mockReturnValue(of(MOCK_EDITION)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const confirmMock = { open: vi.fn().mockReturnValue(of(false)) };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION));

    await TestBed.configureTestingModule({
      imports: [PhaseControlComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => '1' } } } },
        { provide: EditionService, useValue: editionServiceMock },
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
});
