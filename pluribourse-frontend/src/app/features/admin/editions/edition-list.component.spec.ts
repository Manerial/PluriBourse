import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { EditionListComponent } from './edition-list.component';
import { EditionService } from '../../../services/edition.service';
import { ToastService } from '../../../shared/components/toast/toast.service';
import { ConfirmDialogService } from '../../../shared/components/confirm-dialog/confirm-dialog.service';
import { EditionDto } from '../../../models/edition.model';

const MOCK_EDITIONS: EditionDto[] = [
  { id: 1, name: 'Bourse 2026', phase: 'PREPARATION', commissionRate: 20, documentLanguage: 'EN', createdAt: '2026-01-01' }
];

describe('EditionListComponent', () => {
  let fixture: ComponentFixture<EditionListComponent>;
  let component: EditionListComponent;
  let router: Router;

  const editionServiceMock = {
    getAll: vi.fn().mockReturnValue(of(MOCK_EDITIONS)),
    delete: vi.fn().mockReturnValue(of(undefined)),
  };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const confirmMock = { open: vi.fn().mockReturnValue(of(false)) };

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.getAll.mockReturnValue(of(MOCK_EDITIONS));

    await TestBed.configureTestingModule({
      imports: [EditionListComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: EditionService, useValue: editionServiceMock },
        { provide: ToastService, useValue: toastMock },
        { provide: ConfirmDialogService, useValue: confirmMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(EditionListComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
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

  it('navigateToEdit navigates to the edition edit route', () => {
    const spy = vi.spyOn(router, 'navigateByUrl');
    component.navigateToEdit(MOCK_EDITIONS[0]);
    expect(spy).toHaveBeenCalledWith('/admin/editions/1/edit');
  });
});
