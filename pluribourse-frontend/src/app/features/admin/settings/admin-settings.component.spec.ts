import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { AdminSettingsComponent } from './admin-settings.component';
import { GlobalInstanceConfigService } from '../../../services/global-instance-config.service';
import { GlobalInstanceConfigDto } from '../../../models/global-instance-config.model';
import { Language } from '../../../models/language.enum';

const MOCK_CONFIG: GlobalInstanceConfigDto = {
  associationName: 'Mon Asso',
  defaultCommissionRate: 20,
  defaultDocumentLanguage: Language.EN
};

describe('AdminSettingsComponent', () => {
  let fixture: ComponentFixture<AdminSettingsComponent>;
  let component: AdminSettingsComponent;

  const instanceConfigServiceMock = {
    getConfig: vi.fn().mockReturnValue(of(MOCK_CONFIG)),
    updateConfig: vi.fn().mockReturnValue(of(MOCK_CONFIG))
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    instanceConfigServiceMock.getConfig.mockReturnValue(of(MOCK_CONFIG));
    instanceConfigServiceMock.updateConfig.mockReturnValue(of(MOCK_CONFIG));

    await TestBed.configureTestingModule({
      imports: [AdminSettingsComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: GlobalInstanceConfigService, useValue: instanceConfigServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('loads config on init and patches form with returned values', () => {
    expect(instanceConfigServiceMock.getConfig).toHaveBeenCalledTimes(1);
    expect(component.form.getRawValue().associationName).toBe('Mon Asso');
    expect(component.form.getRawValue().defaultCommissionRate).toBe(20);
    expect(component.form.getRawValue().defaultDocumentLanguage).toBe(Language.EN);
    expect(component.isLoading()).toBe(false);
    expect(component.loadError()).toBeNull();
  });

  it('sets loadError key and clears loading when getConfig fails', async () => {
    instanceConfigServiceMock.getConfig.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.loadError()).toBe('admin.settings.error.load');
    expect(component.isLoading()).toBe(false);
  });

  it('calls updateConfig with form values and sets saveSuccess on submit', async () => {
    const updated: GlobalInstanceConfigDto = {
      associationName: 'Nouvelle Asso',
      defaultCommissionRate: 15,
      defaultDocumentLanguage: Language.FR
    };
    instanceConfigServiceMock.updateConfig.mockReturnValue(of(updated));
    component.form.setValue({ associationName: 'Nouvelle Asso', defaultCommissionRate: 15, defaultDocumentLanguage: Language.FR });

    await component.onSubmit();

    expect(instanceConfigServiceMock.updateConfig).toHaveBeenCalledWith({
      associationName: 'Nouvelle Asso',
      defaultCommissionRate: 15,
      defaultDocumentLanguage: Language.FR
    });
    expect(component.saveSuccess()).toBe(true);
    expect(component.saveError()).toBeNull();
    expect(component.isSaving()).toBe(false);
    expect(component.form.getRawValue().associationName).toBe('Nouvelle Asso');
    expect(component.form.getRawValue().defaultCommissionRate).toBe(15);
    expect(component.form.getRawValue().defaultDocumentLanguage).toBe(Language.FR);
  });

  it('sets saveError key and clears saving when updateConfig fails', async () => {
    instanceConfigServiceMock.updateConfig.mockReturnValue(throwError(() => new Error('server error')));
    await component.onSubmit();
    expect(component.saveError()).toBe('admin.settings.error.save');
    expect(component.saveSuccess()).toBe(false);
    expect(component.isSaving()).toBe(false);
  });

  it('does not call updateConfig when form is invalid', async () => {
    component.form.controls.defaultCommissionRate.setValue(150);
    await component.onSubmit();
    expect(instanceConfigServiceMock.updateConfig).not.toHaveBeenCalled();
  });
});
