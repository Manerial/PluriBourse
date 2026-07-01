import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { UserFormComponent } from './user-form.component';
import { UserService } from '../../../services/user.service';
import { UserDto } from '../../../models/user.model';

const MOCK_USER: UserDto = { id: 1, firstName: 'Alice', lastName: 'Smith', username: 'alice', role: 'VOLUNTEER', enabled: true };

describe('UserFormComponent', () => {
  let fixture: ComponentFixture<UserFormComponent>;
  let component: UserFormComponent;
  let router: Router;

  const userServiceMock = {
    createVolunteer: vi.fn().mockReturnValue(of(MOCK_USER)),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    userServiceMock.createVolunteer.mockReturnValue(of(MOCK_USER));

    await TestBed.configureTestingModule({
      imports: [UserFormComponent],
      providers: [
        provideRouter([{ path: 'admin/users', component: UserFormComponent }]),
        provideTranslateService({ lang: 'en' }),
        { provide: UserService, useValue: userServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(UserFormComponent);
    component = fixture.componentInstance;
    router = TestBed.inject(Router);
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('form is invalid when required fields are empty', () => {
    expect(component.form.invalid).toBe(true);
  });

  it('defaults role to VOLUNTEER', () => {
    expect(component.form.controls.role.value).toBe('VOLUNTEER');
  });

  it('form is invalid when the password fails strength rules', () => {
    component.form.controls.firstName.setValue('Alice');
    component.form.controls.lastName.setValue('Smith');
    component.form.controls.username.setValue('alice');
    component.form.controls.password.setValue('short');
    expect(component.form.invalid).toBe(true);
  });

  it('form is valid when all fields satisfy validation rules', () => {
    component.form.controls.firstName.setValue('Alice');
    component.form.controls.lastName.setValue('Smith');
    component.form.controls.username.setValue('alice');
    component.form.controls.password.setValue('Password1');
    expect(component.form.valid).toBe(true);
  });

  it('does not call createVolunteer when the form is invalid', async () => {
    await component.onSubmit();
    expect(userServiceMock.createVolunteer).not.toHaveBeenCalled();
  });

  it('calls createVolunteer with form values on valid submit and navigates to the list', async () => {
    component.form.controls.firstName.setValue('Alice');
    component.form.controls.lastName.setValue('Smith');
    component.form.controls.username.setValue('alice');
    component.form.controls.password.setValue('Password1');
    const navigateSpy = vi.spyOn(router, 'navigate');

    await component.onSubmit();

    expect(userServiceMock.createVolunteer).toHaveBeenCalledWith({
      firstName: 'Alice',
      lastName: 'Smith',
      username: 'alice',
      password: 'Password1',
      role: 'VOLUNTEER',
    });
    expect(navigateSpy).toHaveBeenCalledWith(['/admin/users']);
    expect(component.loading()).toBe(false);
  });

  it('sets error key and stops loading when createVolunteer fails', async () => {
    component.form.controls.firstName.setValue('Alice');
    component.form.controls.lastName.setValue('Smith');
    component.form.controls.username.setValue('alice');
    component.form.controls.password.setValue('Password1');
    userServiceMock.createVolunteer.mockReturnValue(throwError(() => new Error('server')));

    await component.onSubmit();

    expect(component.error()).toBe('admin.users.error.create');
    expect(component.loading()).toBe(false);
  });

  it('cancel() navigates back to the user list', async () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    await component.cancel();
    expect(navigateSpy).toHaveBeenCalledWith(['/admin/users']);
  });
});
