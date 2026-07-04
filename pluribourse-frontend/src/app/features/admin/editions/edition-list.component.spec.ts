import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Dialog } from '@angular/cdk/dialog';
import { provideTranslateService } from '@ngx-translate/core';
import { from, of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EditionListComponent } from './edition-list.component';
import { EditionFormComponent } from './edition-form.component';
import { PhaseControlComponent } from './phase-control/phase-control.component';
import { EditionCategoriesComponent } from './edition-categories/edition-categories.component';
import { EditionService } from '../../../services/edition.service';
import { CurrentEditionService } from '../../../services/current-edition.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionDto } from '../../../models/edition.model';
import { Language } from '../../../models/language.enum';

const MOCK_EDITIONS: EditionDto[] = [
  { id: 1, name: 'Bourse 2026', phase: 'PREPARATION', commissionRate: 20, documentLanguage: Language.EN, createdAt: '2026-01-01', archived: false, startDate: '2026-06-01', endDate: '2026-06-03' }
];

describe('EditionListComponent', () => {
  let fixture: ComponentFixture<EditionListComponent>;
  let component: EditionListComponent;

  const editionServiceMock = {
    getAll: vi.fn().mockReturnValue(of(MOCK_EDITIONS)),
    delete: vi.fn().mockReturnValue(of(undefined)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const confirmMock = { open: vi.fn().mockReturnValue(of(false)) };
  const dialogMock = { open: vi.fn().mockReturnValue({ closed: from(Promise.resolve(undefined)) }) };
  const currentEditionServiceMock = { loadEdition: vi.fn().mockReturnValue(of(undefined)) };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getAll.mockReturnValue(of(MOCK_EDITIONS));
    dialogMock.open.mockReturnValue({ closed: from(Promise.resolve(undefined)) });
    currentEditionServiceMock.loadEdition.mockReturnValue(of(undefined));

    await TestBed.configureTestingModule({
      imports: [EditionListComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: EditionService, useValue: editionServiceMock },
        { provide: CurrentEditionService, useValue: currentEditionServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ConfirmDialogService, useValue: confirmMock },
        { provide: Dialog, useValue: dialogMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditionListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads editions on init', () => {
    expect(editionServiceMock.getAll).toHaveBeenCalledTimes(1);
    expect(component.editions().length).toBe(1);
    expect(component.error()).toBeNull();
  });

  it('sets error key when load fails', async () => {
    editionServiceMock.getAll.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('edition.actions.error.load');
  });

  it('isEditable returns true only for PREPARATION phase', () => {
    expect(component.isEditable(MOCK_EDITIONS[0])).toBe(true);
    expect(component.isEditable({ ...MOCK_EDITIONS[0], phase: 'DEPOSIT' })).toBe(false);
  });

  it('openEditDialog opens EditionFormComponent with the edition id', () => {
    component.openEditDialog(MOCK_EDITIONS[0]);
    expect(dialogMock.open).toHaveBeenCalledWith(
      EditionFormComponent,
      expect.objectContaining({ data: { editionId: 1 } })
    );
  });

  it('openCreateDialog opens EditionFormComponent with editionId null', () => {
    component.openCreateDialog();
    expect(dialogMock.open).toHaveBeenCalledWith(
      EditionFormComponent,
      expect.objectContaining({ data: { editionId: null } })
    );
  });

  it('reloads the edition list after the edition dialog closes', async () => {
    editionServiceMock.getAll.mockClear();
    component.openCreateDialog();
    await fixture.whenStable();
    expect(editionServiceMock.getAll).toHaveBeenCalledTimes(1);
  });

  it('refreshes the current edition (topbar chip) after the create/edit dialog closes', async () => {
    currentEditionServiceMock.loadEdition.mockClear();
    component.openCreateDialog();
    await fixture.whenStable();
    expect(currentEditionServiceMock.loadEdition).toHaveBeenCalledTimes(1);
  });

  it('openPhaseDialog opens PhaseControlComponent with the edition id', () => {
    component.openPhaseDialog(MOCK_EDITIONS[0]);
    expect(dialogMock.open).toHaveBeenCalledWith(
      PhaseControlComponent,
      expect.objectContaining({ data: { editionId: 1 } })
    );
  });

  it('openCategoriesDialog opens EditionCategoriesComponent with the edition id', () => {
    component.openCategoriesDialog(MOCK_EDITIONS[0]);
    expect(dialogMock.open).toHaveBeenCalledWith(
      EditionCategoriesComponent,
      expect.objectContaining({ data: { editionId: 1 } })
    );
  });

  it('reloads the edition list after the categories dialog closes', async () => {
    editionServiceMock.getAll.mockClear();
    component.openCategoriesDialog(MOCK_EDITIONS[0]);
    await fixture.whenStable();
    expect(editionServiceMock.getAll).toHaveBeenCalledTimes(1);
  });

  it('confirmDelete removes the edition from the local list without re-fetching', async () => {
    confirmMock.open.mockReturnValue(of(true));
    component.confirmDelete(MOCK_EDITIONS[0]);
    await fixture.whenStable();
    expect(editionServiceMock.delete).toHaveBeenCalledWith(1);
    expect(component.editions().length).toBe(0);
    expect(toastMock.showSuccess).toHaveBeenCalled();
    expect(editionServiceMock.getAll).toHaveBeenCalledTimes(1);
  });

  it('confirmDelete keeps the local list untouched and shows an error toast when delete fails', async () => {
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.delete.mockReturnValue(throwError(() => new Error('server')));
    component.confirmDelete(MOCK_EDITIONS[0]);
    await fixture.whenStable();
    expect(component.editions().length).toBe(1);
    expect(toastMock.showError).toHaveBeenCalled();
  });

  it('confirmDelete refreshes the current edition (topbar chip) after a successful delete', async () => {
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.delete.mockReturnValue(of(undefined));
    currentEditionServiceMock.loadEdition.mockClear();
    component.confirmDelete(MOCK_EDITIONS[0]);
    await fixture.whenStable();
    expect(currentEditionServiceMock.loadEdition).toHaveBeenCalledTimes(1);
  });

  it('confirmDelete does not refresh the current edition when delete fails', async () => {
    confirmMock.open.mockReturnValue(of(true));
    editionServiceMock.delete.mockReturnValue(throwError(() => new Error('server')));
    currentEditionServiceMock.loadEdition.mockClear();
    component.confirmDelete(MOCK_EDITIONS[0]);
    await fixture.whenStable();
    expect(currentEditionServiceMock.loadEdition).not.toHaveBeenCalled();
  });
});
