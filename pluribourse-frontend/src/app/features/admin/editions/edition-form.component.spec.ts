import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { HttpErrorResponse } from '@angular/common/http';
import { EditionFormComponent } from './edition-form.component';
import { EditionService } from '../../../services/edition.service';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';
import { ToastService } from '../../../shared/components/toast/toast.service';

const MOCK_CONFIG = { associationName: 'Test', defaultCommissionRate: 20, defaultDocumentLanguage: 'EN' };

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

  beforeEach(async () => {
    vi.clearAllMocks();
    editionServiceMock.create.mockReturnValue(of({}));
    instanceConfigMock.getConfig.mockReturnValue(of(MOCK_CONFIG));
    await TestBed.configureTestingModule({
      imports: [EditionFormComponent],
      providers: [
        provideRouter([{ path: 'admin/editions', component: EditionFormComponent }]),
        provideTranslateService({ lang: 'en' }),
        { provide: EditionService, useValue: editionServiceMock },
        { provide: GlobalInstanceConfigService, useValue: instanceConfigMock },
        { provide: ToastService, useValue: toastMock },
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

  it('calls editionService.create with form values on valid submit', async () => {
    component.form.controls.name.setValue('Bourse 2026');
    await component.onSubmit();
    expect(editionServiceMock.create).toHaveBeenCalledWith({
      name: 'Bourse 2026',
      commissionRate: 20,
      documentLanguage: 'EN',
    });
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('sets formError key on 422 response (active edition already exists)', async () => {
    editionServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 422 })));
    component.form.controls.name.setValue('Bourse 2027');
    await component.onSubmit();
    expect(component.formError()).toBe('edition.create.error.alreadyActive');
    expect(toastMock.showError).not.toHaveBeenCalled();
  });

  it('shows error toast on non-422 API error', async () => {
    editionServiceMock.create.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
    component.form.controls.name.setValue('Bourse 2027');
    await component.onSubmit();
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(component.formError()).toBeNull();
  });

  it('isSaving is false after submit completes', async () => {
    component.form.controls.name.setValue('Bourse 2026');
    await component.onSubmit();
    expect(component.isSaving()).toBe(false);
  });
});
