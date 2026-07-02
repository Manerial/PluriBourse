import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { SellerFormComponent } from './seller-form.component';
import { SellerService } from '../../../services/seller.service';
import { SellerDto } from '../../../models/seller.model';

const MOCK_SELLER: SellerDto = { id: 1, firstName: 'Pierre', lastName: 'Martin', email: 'martin.pierre@email.com', phone: '0612345678' };

describe('SellerFormComponent', () => {
  let fixture: ComponentFixture<SellerFormComponent>;
  let component: SellerFormComponent;

  const sellerServiceMock = {
    create: vi.fn().mockReturnValue(of(MOCK_SELLER)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    sellerServiceMock.create.mockReturnValue(of(MOCK_SELLER));

    await TestBed.configureTestingModule({
      imports: [SellerFormComponent],
      providers: [
        provideTranslateService({ lang: 'en' }),
        { provide: SellerService, useValue: sellerServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SellerFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('form is invalid when required fields are empty', () => {
    expect(component.form.invalid).toBe(true);
  });

  it('form is invalid when email format is wrong', () => {
    component.form.controls.firstName.setValue('Pierre');
    component.form.controls.lastName.setValue('Martin');
    component.form.controls.email.setValue('not-an-email');
    component.form.controls.phone.setValue('0612345678');
    expect(component.form.invalid).toBe(true);
  });

  it('form is valid when all fields satisfy validation rules', () => {
    component.form.controls.firstName.setValue('Pierre');
    component.form.controls.lastName.setValue('Martin');
    component.form.controls.email.setValue('martin.pierre@email.com');
    component.form.controls.phone.setValue('0612345678');
    expect(component.form.valid).toBe(true);
  });

  it('does not call create when the form is invalid', async () => {
    await component.onSubmit();
    expect(sellerServiceMock.create).not.toHaveBeenCalled();
  });

  it('calls create and emits created seller on valid submit', async () => {
    component.form.controls.firstName.setValue('Pierre');
    component.form.controls.lastName.setValue('Martin');
    component.form.controls.email.setValue('martin.pierre@email.com');
    component.form.controls.phone.setValue('0612345678');
    const createdSpy = vi.fn();
    component.created.subscribe(createdSpy);

    await component.onSubmit();

    expect(sellerServiceMock.create).toHaveBeenCalledWith({
      firstName: 'Pierre',
      lastName: 'Martin',
      email: 'martin.pierre@email.com',
      phone: '0612345678',
    });
    expect(createdSpy).toHaveBeenCalledWith(MOCK_SELLER);
    expect(component.loading()).toBe(false);
  });

  it('sets error key and stops loading when create fails', async () => {
    component.form.controls.firstName.setValue('Pierre');
    component.form.controls.lastName.setValue('Martin');
    component.form.controls.email.setValue('martin.pierre@email.com');
    component.form.controls.phone.setValue('0612345678');
    sellerServiceMock.create.mockReturnValue(throwError(() => new Error('server')));

    await component.onSubmit();

    expect(component.error()).toBe('volunteer.deposit.form.error.create');
    expect(component.loading()).toBe(false);
  });

  it('cancel() emits cancelled', () => {
    const cancelledSpy = vi.fn();
    component.cancelled.subscribe(cancelledSpy);
    component.cancel();
    expect(cancelledSpy).toHaveBeenCalled();
  });
});
