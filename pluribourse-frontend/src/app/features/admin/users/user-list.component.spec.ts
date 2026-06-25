import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideTranslateService } from '@ngx-translate/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';
import { UserListComponent } from './user-list.component';
import { UserService } from '../../../services/user.service';
import { UserDto } from '../../../models/user.model';
import { ToastService } from '../../../shared/components/toast/toast.service';

const MOCK_VOLUNTEERS: UserDto[] = [
  { id: 1, firstName: 'Alice', lastName: 'Smith', username: 'alice', role: 'VOLUNTEER', enabled: true },
  { id: 2, firstName: 'Bob', lastName: 'Dupont', username: 'bob', role: 'VOLUNTEER', enabled: false }
];

describe('UserListComponent', () => {
  let fixture: ComponentFixture<UserListComponent>;
  let component: UserListComponent;

  const userServiceMock = {
    getVolunteers: vi.fn().mockReturnValue(of(MOCK_VOLUNTEERS)),
    disableVolunteer: vi.fn().mockReturnValue(of(undefined)),
    enableVolunteer: vi.fn().mockReturnValue(of(undefined)),
    resetPassword: vi.fn().mockReturnValue(of(undefined))
  };

  const toastMock = {
    showSuccess: vi.fn(),
    showError: vi.fn(),
  };

  beforeEach(async () => {
    vi.clearAllMocks();
    userServiceMock.getVolunteers.mockReturnValue(of(MOCK_VOLUNTEERS));

    await TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [
        provideRouter([]),
        provideTranslateService({ lang: 'en' }),
        { provide: UserService, useValue: userServiceMock },
        { provide: ToastService, useValue: toastMock },
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(UserListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('renders the volunteer list on init', () => {
    expect(userServiceMock.getVolunteers).toHaveBeenCalledTimes(1);
    expect(component.users().length).toBe(2);
    expect(component.error()).toBeNull();
  });

  it('shows error key when load fails', async () => {
    userServiceMock.getVolunteers.mockReturnValue(throwError(() => new Error('network')));
    await component.ngOnInit();
    expect(component.error()).toBe('admin.users.error.load');
  });

  it('disable button calls disableVolunteer when user is enabled', async () => {
    const enabledUser = MOCK_VOLUNTEERS[0];
    await component.toggleEnabled(enabledUser);
    expect(userServiceMock.disableVolunteer).toHaveBeenCalledWith(1);
  });

  it('shows success toast after disabling a user', async () => {
    await component.toggleEnabled(MOCK_VOLUNTEERS[0]);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('enable button calls enableVolunteer when user is disabled', async () => {
    const disabledUser = MOCK_VOLUNTEERS[1];
    await component.toggleEnabled(disabledUser);
    expect(userServiceMock.enableVolunteer).toHaveBeenCalledWith(2);
  });

  it('shows success toast after enabling a user', async () => {
    await component.toggleEnabled(MOCK_VOLUNTEERS[1]);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
  });

  it('shows error toast when toggle fails', async () => {
    userServiceMock.disableVolunteer.mockReturnValue(throwError(() => new Error('server')));
    await component.toggleEnabled(MOCK_VOLUNTEERS[0]);
    expect(toastMock.showError).toHaveBeenCalledOnce();
    expect(toastMock.showSuccess).not.toHaveBeenCalled();
  });

  it('showResetPassword sets resetPasswordFor signal', () => {
    component.showResetPassword(1);
    expect(component.resetPasswordFor()).toBe(1);
  });

  it('shows success toast after resetting password', async () => {
    component.showResetPassword(1);
    component.resetPasswordForm.setValue({ newPassword: 'NewPass1' });
    await component.submitResetPassword(1);
    expect(toastMock.showSuccess).toHaveBeenCalledOnce();
    expect(component.resetPasswordFor()).toBeNull();
  });
});
