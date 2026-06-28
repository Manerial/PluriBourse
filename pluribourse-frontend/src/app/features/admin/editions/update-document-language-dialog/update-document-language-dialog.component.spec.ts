import { TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { DialogRef, DIALOG_DATA } from '@angular/cdk/dialog';
import { vi } from 'vitest';
import {
  UpdateDocumentLanguageDialogComponent,
  UpdateDocumentLanguageDialogData
} from './update-document-language-dialog.component';

const testData: UpdateDocumentLanguageDialogData = { editionId: 1, currentLanguage: 'EN' };

describe('UpdateDocumentLanguageDialogComponent', () => {
  const mockClose = vi.fn();

  beforeEach(async () => {
    vi.clearAllMocks();
    await TestBed.configureTestingModule({
      imports: [UpdateDocumentLanguageDialogComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: DialogRef, useValue: { close: mockClose } },
        { provide: DIALOG_DATA, useValue: testData },
      ],
    }).compileComponents();
  });

  it('initializes form with currentLanguage', () => {
    const fixture = TestBed.createComponent(UpdateDocumentLanguageDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.form.getRawValue().documentLanguage).toBe('EN');
  });

  it('confirm() closes dialog with the selected language', () => {
    const fixture = TestBed.createComponent(UpdateDocumentLanguageDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.documentLanguage.setValue('FR');
    fixture.componentInstance.confirm();
    expect(mockClose).toHaveBeenCalledWith('FR');
  });

  it('cancel() closes dialog with undefined', () => {
    const fixture = TestBed.createComponent(UpdateDocumentLanguageDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(mockClose).toHaveBeenCalledWith(undefined);
  });
});
