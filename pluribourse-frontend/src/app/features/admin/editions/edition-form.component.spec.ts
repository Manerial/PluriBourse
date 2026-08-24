import { TestBed, ComponentFixture } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { provideNativeDateAdapter } from '@angular/material/core';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { EditionFormComponent } from './edition-form.component';
import { EditionService } from '../../../services/edition.service';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

const MOCK_CONFIG = { associationName: 'Test', defaultCommissionRate: 20, defaultDocumentLanguage: 'EN' };

const MOCK_EDITION = {
  id: 1,
  name: 'Bourse Printemps',
  commissionRate: 15,
  documentLanguage: 'FR' as const,
  startDate: '2026-10-01',
  endDate: '2026-10-03',
  phase: 'PREPARATION' as const,
};

describe('EditionFormComponent', () => {
  let fixture: ComponentFixture<EditionFormComponent>;
  let component: EditionFormComponent;

  const editionServiceMock = {
    create: vi.fn().mockReturnValue(of({})),
    getById: vi.fn(),
    update: vi.fn(),
  };
  const instanceConfigMock = { getConfig: vi.fn().mockReturnValue(of(MOCK_CONFIG)) };
  const toastMock = { showSuccess: vi.fn(), showError: vi.fn() };
  const dialogRefMock = { close: vi.fn() };

  describe('create mode', () => {
    beforeEach(async () => {
      vi.clearAllMocks();
      editionServiceMock.create.mockReturnValue(of({}));
      instanceConfigMock.getConfig.mockReturnValue(of(MOCK_CONFIG));
      await TestBed.configureTestingModule({
        imports: [EditionFormComponent],
        providers: [
          provideTranslateService({ lang: 'en' }),
          provideNativeDateAdapter(),
          { provide: EditionService, useValue: editionServiceMock },
          { provide: GlobalInstanceConfigService, useValue: instanceConfigMock },
          { provide: ToastService, useValue: toastMock },
          { provide: DIALOG_DATA, useValue: { editionId: null } },
          { provide: DialogRef, useValue: dialogRefMock },
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(EditionFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('form is invalid when name is empty', () => {
      expect(component.form.invalid).toBe(true);
    });

    it('pre-fills commissionRate and documentLanguage from instance config', () => {
      expect(component.form.controls.commissionRate.value).toBe(20);
      expect(component.form.controls.documentLanguage.value).toBe('EN');
    });

    function fillRequiredDates(): void {
      component.form.controls.startDate.setValue(new Date(2026, 9, 1));
      component.form.controls.endDate.setValue(new Date(2026, 9, 3));
    }

    it('form is invalid when dates are empty', () => {
      component.form.controls.name.setValue('Bourse 2026');
      expect(component.form.invalid).toBe(true);
    });

    it('calls editionService.create with form values on valid submit', async () => {
      component.form.controls.name.setValue('Bourse 2026');
      fillRequiredDates();
      await component.onSubmit();
      expect(editionServiceMock.create).toHaveBeenCalledWith({
        name: 'Bourse 2026',
        commissionRate: 20,
        documentLanguage: 'EN',
        startDate: '2026-10-01',
        endDate: '2026-10-03',
      });
      expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    });

    it('sets formError key on 422 response (active edition already exists)', async () => {
      editionServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({
        status: 422,
        error: { type: 'https://pluribourse/errors/edition-already-active' }
      })));
      component.form.controls.name.setValue('Bourse 2027');
      fillRequiredDates();
      await component.onSubmit();
      expect(component.formError()).toBe('edition.create.error.alreadyActive');
      expect(toastMock.showError).not.toHaveBeenCalled();
    });

    it('sets formError key on 422 response (association name not configured)', async () => {
      editionServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({
        status: 422,
        error: { type: 'https://pluribourse/errors/association-name-not-configured' }
      })));
      component.form.controls.name.setValue('Bourse 2027');
      fillRequiredDates();
      await component.onSubmit();
      expect(component.formError()).toBe('edition.create.error.associationNameNotConfigured');
      expect(toastMock.showError).not.toHaveBeenCalled();
    });

    it('shows error toast on non-422 API error', async () => {
      editionServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
      component.form.controls.name.setValue('Bourse 2027');
      fillRequiredDates();
      await component.onSubmit();
      expect(toastMock.showError).toHaveBeenCalledOnce();
      expect(component.formError()).toBeNull();
    });

    it('isSaving is false after submit completes', async () => {
      component.form.controls.name.setValue('Bourse 2026');
      fillRequiredDates();
      await component.onSubmit();
      expect(component.isSaving()).toBe(false);
    });

    it('cancel() closes the dialog', () => {
      component.cancel();
      expect(dialogRefMock.close).toHaveBeenCalledOnce();
    });

    it('closes the dialog after a successful create', async () => {
      component.form.controls.name.setValue('Bourse 2026');
      fillRequiredDates();
      await component.onSubmit();
      expect(dialogRefMock.close).toHaveBeenCalledOnce();
    });
  });

  describe('edit mode', () => {
    beforeEach(async () => {
      vi.clearAllMocks();
      editionServiceMock.getById.mockReturnValue(of(MOCK_EDITION));
      editionServiceMock.update.mockReturnValue(of({}));
      await TestBed.configureTestingModule({
        imports: [EditionFormComponent],
        providers: [
          provideTranslateService({ lang: 'en' }),
          provideNativeDateAdapter(),
          { provide: EditionService, useValue: editionServiceMock },
          { provide: GlobalInstanceConfigService, useValue: instanceConfigMock },
          { provide: ToastService, useValue: toastMock },
          { provide: DIALOG_DATA, useValue: { editionId: 1 } },
          { provide: DialogRef, useValue: dialogRefMock },
        ],
      }).compileComponents();
      fixture = TestBed.createComponent(EditionFormComponent);
      component = fixture.componentInstance;
      fixture.detectChanges();
      await fixture.whenStable();
    });

    it('pre-fills the form from the loaded edition', () => {
      expect(component.form.controls.name.value).toBe('Bourse Printemps');
      expect(component.form.controls.commissionRate.value).toBe(15);
      expect(component.form.controls.documentLanguage.value).toBe('FR');
      expect(component.form.controls.startDate.value).toEqual(new Date(2026, 9, 1));
      expect(component.form.controls.endDate.value).toEqual(new Date(2026, 9, 3));
    });

    it('sets the dialog title to the loaded edition name', () => {
      expect(component.dialogTitle()).toBe('Bourse Printemps');
    });

    it('calls editionService.update and closes the dialog in edit mode', async () => {
      await component.onSubmit();
      expect(editionServiceMock.update).toHaveBeenCalledWith(1, {
        name: 'Bourse Printemps',
        commissionRate: 15,
        documentLanguage: 'FR',
        startDate: '2026-10-01',
        endDate: '2026-10-03',
      });
      expect(dialogRefMock.close).toHaveBeenCalledOnce();
    });

    it('sets formError key on 422 response when the edition has moved past Preparation', async () => {
      // Defensive case: the edit dialog is only opened from a Preparation-phase row in the list,
      // but the phase can advance in another session/tab between that click and this submit.
      editionServiceMock.update.mockReturnValue(throwError(() => new HttpErrorResponse({
        status: 422,
        error: { type: 'https://pluribourse/errors/edition-cannot-be-updated' }
      })));
      await component.onSubmit();
      expect(component.formError()).toBe('edition.edit.error.cannotUpdate');
      expect(toastMock.showError).not.toHaveBeenCalled();
      expect(dialogRefMock.close).not.toHaveBeenCalled();
    });
  });
});
